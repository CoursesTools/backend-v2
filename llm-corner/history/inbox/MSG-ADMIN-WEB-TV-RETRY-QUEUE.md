# Admin Web → Backend: TradingView Retry Queue Admin Page

> From: Backend agent
> To: Admin-web agent
> Created: 2026-04-17
> Re: New "TradingView Retry Queue" page in admin panel — view pending/dead TV bot retry jobs + force re-run button

---

## Goal

Build a **TradingView Retry Queue** page in the admin panel. Context: when the TradingView bot is unreachable or returns an error, the backend no longer rolls back the subscription grant. Instead it writes a durable row into `trading_view_retry_jobs` and a scheduler drains the queue once a minute with exponential backoff. Admins need to:

1. See what's pending (users whose TV access has not yet been synced).
2. See what's DEAD (hit max attempts — needs manual intervention).
3. **Force re-run** any job immediately (e.g., after the bot is restored, or the underlying TradingView username was corrected).
4. Drop a job that's no longer relevant (e.g., the user's subscription was later terminated).

---

## Endpoints

Base path: `/api/v1/admin/tv-retry` (backend runs under `/api`). All endpoints require `Authorization: Bearer <access_token>` with role `ADMIN` — non-admin JWTs get `403 Forbidden`.

### 1. `GET /api/v1/admin/tv-retry/jobs`

Paginated list with optional filters.

| Param     | Type            | Notes |
|-----------|-----------------|-------|
| `userId`  | `integer`       | Exact match |
| `status`  | `enum`          | `PENDING` \| `DEAD` |
| `type`    | `enum`          | `ACTIVATE` \| `RENAME` |
| `page`    | `integer`       | Zero-based, default `0` |
| `size`    | `integer`       | Default `20`, max `100` |
| `sort`    | `string`        | `property,(asc\|desc)`. Default: `nextAttemptAt,asc`. Allowed: `id`, `nextAttemptAt`, `firstEnqueuedAt`, `attempts`, `status`, `type` |

Response: standard `PageDto<TradingViewRetryJobReadDto>` wrapper (same shape as `/v1/admin/orders`):

```json
{
  "content": [ /* TradingViewRetryJobReadDto[] */ ],
  "pageNumber": 0,
  "pageSize": 20,
  "totalPages": 3,
  "totalElements": 42,
  "isFirst": true,
  "isLast": false
}
```

### 2. `GET /api/v1/admin/tv-retry/jobs/{id}`

Returns single `TradingViewRetryJobReadDto` or `404` if not found.

### 3. `POST /api/v1/admin/tv-retry/jobs/{id}/retry` — **Force re-run button**

Resets the clicked row to `status=PENDING`, `nextAttemptAt=now()`, `attempts=0`, clears `lastError`, and **increments `forceRetryCount`**. The scheduler picks it up on its next tick (≤ 60 seconds). `firstEnqueuedAt` is preserved.

- Works on both `PENDING` (move to "run now") and `DEAD` (resurrect) rows.
- **DEAD supersede edge case:** if the clicked row is DEAD and a fresher PENDING job exists for the same user+type (e.g., a newer grant came in while this one was parked), the backend drops the DEAD row and applies the retry to the PENDING row instead. The response in that case is the **PENDING row**, with its own `id` — not the originally clicked one. Update the row in the UI by id from the response, don't assume it's the same id you POSTed.
- **Counter semantics:** `attempts` tracks automatic retries in the current cycle (resets to 0 on force-retry). `forceRetryCount` tracks lifetime manual re-runs (never reset by the scheduler). If you want to surface "this job was force-retried 3 times", use `forceRetryCount`.
- Response: 200 + updated `TradingViewRetryJobReadDto`.
- 404 if job id doesn't exist, or if the scheduler completed (and deleted) the job between your click and the backend processing your request. UI should treat this as "success, refresh the list".

### 4. `DELETE /api/v1/admin/tv-retry/jobs/{id}`

Drops the job entirely. Idempotent (single atomic DELETE) so a race with the scheduler's own deletion on successful retry can't crash. Use when the underlying intent is no longer valid (e.g., the subscription has been terminated and the user does not need TV access anymore).

- Response: 204 No Content.
- 404 if job id doesn't exist (including the case where the scheduler deleted it right before your click). UI: treat as success, refresh the list.

---

## `TradingViewRetryJobReadDto` schema

```json
{
  "id": 42,
  "userId": 123,
  "userEmail": "user@example.com",
  "tradingViewName": "tv_user_123",
  "type": "ACTIVATE",                // or "RENAME"
  "status": "PENDING",               // or "DEAD"
  "attempts": 2,
  "forceRetryCount": 1,
  "nextAttemptAt": "2026-04-17T22:05:00",
  "firstEnqueuedAt": "2026-04-17T21:42:00",
  "lastError": "TV bot unreachable (ResourceAccessException): Connection refused",
  "payload": "{\"email\":\"user@example.com\",\"tier\":\"PRO\",\"tv\":\"tv_user_123\",\"expiration\":\"2027-04-17T00:00:00\"}"
}
```

**Field notes for the UI:**
- `attempts`: automatic-retry failures in the **current** cycle (max 10 before DEAD). Resets to 0 whenever the admin clicks Retry Now, or when a fresher enqueue supersedes a pending row.
- `forceRetryCount`: **lifetime** admin manual re-runs. Never reset by the scheduler. Surface as an audit counter e.g., "Manual retries: 3".
- `payload` is the raw JSON body that will be POSTed to the TradingView bot on retry. For `ACTIVATE` jobs it carries `email`, `tier`, `tv` (TradingView name), `expiration`. For `RENAME` jobs it carries `old`, `new`, `tier`, `expiration`. Surface it in a collapsible "Payload" details area — useful for debugging.
- `lastError` is truncated to 2048 chars on the backend. Wrap in a `<pre>` or collapsible block.
- `status=DEAD` semantically means **"idle / parked"** — the automatic retry cycle gave up, but the row is retained forever and can be force-retried at any time. Per our decision: **always grey for DEAD/Idle** (simple, matches "parked" semantics — the error text lives in the row, color communicates state).
- **"Action required" subcategory**: DEAD rows created directly via `enqueueDead` (not via automatic retry exhaustion) carry `attempts=0` and a `lastError` that starts with `"TradingView username '..' was not found on TradingView"`. These are **permanent input errors** (user/admin mistyped the nickname) and need admin intervention *before* a force-retry will succeed. Recommend a distinct visual treatment — e.g., a red "⚠ Action required" badge on the row, or the top of the DEAD pile — so operators can triage them first. Detection: `status == DEAD && attempts == 0 && lastError contains "was not found on TradingView"`.

---

## Backoff schedule (so the UI can show "next retry in X")

The backend retries on this schedule, indexed by `attempts`:

| attempts before retry | wait until next try |
|-----------------------|---------------------|
| 0 → 1                 | 1 minute            |
| 1 → 2                 | 5 minutes           |
| 2 → 3                 | 15 minutes          |
| 3 → 4                 | 1 hour              |
| 4 → 5                 | 6 hours             |
| 5 → 6 and beyond      | 24 hours            |

Max attempts = 10 → job moves to `DEAD` (we recommend labeling it "Idle" in the UI). Worst-case time to DEAD ≈ 5.3 days. Config key: `tradingview.retry.max-attempts` (backend-side, just so you know it can change).

DEAD/Idle rows are **never auto-purged**. They persist forever unless the admin explicitly Drops them or force-retries them (which resurrects to PENDING with `attempts=0` + `forceRetryCount` incremented). This is intentional — we want to keep the history of parked jobs so they can be re-run in the future.

A simpler UX signal: display `nextAttemptAt` directly as "Next retry: in 4 min" (use relative time). You don't need to hard-code the table above.

---

## UI suggestions

**Table columns:**
1. User email + TV name (stacked, monospace for TV name)
2. Type pill (ACTIVATE / RENAME, different colors)
3. Status pill (PENDING = amber, DEAD = grey with "Idle" label)
4. Attempts (e.g., `3 / 10`) — current-cycle automatic retries
5. Force retries (e.g., `×2`) — lifetime admin manual re-runs; hide column if zero across the page to reduce noise
6. Next retry (relative time; for DEAD/Idle rows, show "—")
7. First enqueued (relative time, tooltip with absolute)
8. Last error (truncated single line with tooltip/modal for full text)
9. Actions: **[Retry now]** + **[Drop]** buttons

**Filter pills** at the top: `All | Pending | Idle (Dead)`. Default to `Pending` since that's where live work is. A count badge on the "Idle" pill is valuable (if there are parked rows, admin should see it).

**Auto-refresh**: poll every 30s while on the page. Disable polling if the user starts interacting (avoids yanking a row out of their click target).

**Confirmation dialog**: `Drop` should confirm; `Retry now` should not (it's non-destructive — just schedules a retry).

**Empty state**: "No jobs in queue — all TV syncs are up to date." Friendly.

**Detail view**: clicking a row opens a drawer with the full payload JSON, full lastError, and the action buttons.

---

## How jobs get created (just for context)

- **Payment webhooks (Stripe / Crypto):** when the webhook persists an order + subscription grant, it calls the TradingView bot. If the bot fails (network or 5xx), the subscription is still committed as `GRANTED` and a `PENDING` row is inserted. No more "paid but not granted" state.
- **Admin manual grants:** `POST /v1/admin/access` follows the same path — subscription is saved, TV sync is queued on failure.
- **TradingView username changes:** `POST /v1/users/trading-view` (user updates their TV name) also enqueues on bot failure.

De-dup: only **one PENDING row per `(user_id, type)`** at a time. A fresher enqueue (e.g., the user re-renews during retry backoff) overwrites the stale payload so we never activate the wrong expiration date.

---

## Error handling

- `404` on single-job endpoints → show toast "Job no longer exists" and remove the row.
- `400` on sort params → backend rejects anything outside the allowed list.
- `403` → redirect to login (shouldn't happen if the admin JWT is valid).

---

Ping me if you need anything — sample responses, additional filter params, or a separate "DEAD job summary" endpoint for the dashboard header.
