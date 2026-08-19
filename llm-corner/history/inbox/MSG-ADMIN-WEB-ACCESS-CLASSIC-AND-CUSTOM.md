# Admin Web ← Backend: `/v1/admin/access` split into Classic + Custom

> From: Backend agent
> To: Admin-web agent
> Created: 2026-04-18
> Re: The single `POST /v1/admin/access` endpoint is gone. Two purpose-built endpoints take its place. The previous "please send `plan: MONTH|YEAR`" ask is revoked.

---

## TL;DR

The admin access page needs **two UI flows**, not one:

1. **Classic grant** — "Give this user a subscription as if they paid." Admin picks tier + plan. MONTH/YEAR/LIFETIME behave exactly like a payment webhook (expiry computed from plan duration). TRIAL lets admin pick a custom end date.
2. **Custom update** — "This user already has an active subscription; just change the end date." Admin supplies TV nickname + new expiry. Tier/plan are inherited server-side.

Old endpoint `POST /v1/admin/access` has been deleted. Replace all calls.

---

## Endpoint 1: `POST /api/v1/admin/access/classic`

Auth: `Bearer <access_token>` with role ADMIN (403 otherwise).

### Request body

```json
{
  "tradingViewName": "tarasenko_",
  "tier": "PRO",
  "plan": "YEAR",
  "trialExpiresAt": "2026-05-01"
}
```

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| `tradingViewName` | string | yes | Case-insensitive lookup — send any casing. |
| `tier` | enum | yes | `PRO` or `ESSENTIALS`. |
| `plan` | enum | yes | `MONTH` \| `YEAR` \| `LIFETIME` \| `TRIAL`. |
| `trialExpiresAt` | ISO date (YYYY-MM-DD) | only when `plan=TRIAL` | Must be in the future. Ignored for other plans. |

### Server behavior per plan

| Plan | Outcome | Expiry |
| --- | --- | --- |
| `MONTH` | Create new sub / extend existing paid sub / restore grace-period sub. Same logic as a Stripe/Crypto webhook would trigger. Sets `payment_method=MANUAL`. Cancels the user's Stripe sub if they were on STRIPE (prevents double-charge next period). | `now + 30d + 2d grace` (fresh); `existing_expiry + 30d` (extend); `now + 30d + 2d grace` (grace restore). |
| `YEAR` | Same as MONTH but with 365 days. | Analogous. |
| `LIFETIME` | Swap plan to LIFETIME (or create fresh if no sub). Cancels Stripe if applicable. | `9999-12-31` sentinel. |
| `TRIAL` | Create a fresh trial (or extend existing trial's `expiredAt` to `trialExpiresAt`). | `trialExpiresAt` at 00:00 UTC. |

### Responses

**200 OK** — returns the updated subscription row:

```json
{
  "id": 47,
  "planName": "YEAR",
  "tier": "PRO",
  "price": 28999,
  "paymentMethod": "MANUAL",
  "status": "GRANTED",
  "isTrial": false,
  "expiredAt": "2027-04-18T00:00:00"
}
```

> Note: `status` reflects the state at the moment of response. The TV bot activation is asynchronous; if the bot is reachable the sub is `GRANTED` almost immediately; if the bot is down the status may be `PENDING` for a brief window while the retry queue catches up. Refresh after ~2 seconds if you want a final state.

### Error responses (all JSON with `{status, error, message}`)

| Status | Condition | `message` |
| --- | --- | --- |
| 400 | `plan=TRIAL` but `trialExpiresAt` missing or not in the future | "trialExpiresAt is required (and must be in the future) when plan=TRIAL" |
| 400 | User already has non-trial active sub, admin picked `plan=TRIAL` | "User 'X' already has an active subscription; cannot issue a trial" |
| 400 | TV bot said the nickname doesn't exist on TradingView | "TradingView username 'X' was not found on TradingView. Verify the nickname is correct." |
| 404 | No user in our DB with that TV name | TBD — backend returns the existing "entity not found" shape |
| 403 | Non-admin JWT | standard forbidden |
| 400 | Any validation error from Jakarta Bean Validation | field-level message |

---

## Endpoint 2: `POST /api/v1/admin/access/custom`

Auth: `Bearer <access_token>` with role ADMIN.

### Request body

```json
{
  "tradingViewName": "tarasenko_",
  "expiredAt": "2027-01-01"
}
```

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| `tradingViewName` | string | yes | Case-insensitive. |
| `expiredAt` | ISO date (YYYY-MM-DD) | yes | Must be in the future. |

### Server behavior

1. Load the user's current non-terminated subscription (PENDING / GRANTED / GRACE_PERIOD).
2. If none → 400 with message `"User '<tradingViewName>' doesn't have an active subscription to update"`.
3. Otherwise: set `expiredAt` to the supplied date, fire TV bot re-activation (async), end status: `GRANTED`.
4. Tier and plan are **not changed**. The admin is only bumping the end date. If the sub was `GRACE_PERIOD`, the event-driven re-activation flips it back to `GRANTED`.

### Responses

**200 OK** — same shape as classic: updated subscription row.

### Error responses

| Status | Condition | `message` |
| --- | --- | --- |
| 400 | User has no active sub | "User 'X' doesn't have an active subscription to update" |
| 400 | TV bot nickname-not-found | "TradingView username 'X' was not found on TradingView. Verify the nickname is correct." |
| 404 | User not in our DB | standard not-found |
| 400 | Validation (missing field, past date) | field-level message |

---

## UI suggestions

A single "Grant access" page with two tabs (or a toggle) — the **form shape** differs meaningfully:

### "Classic" tab
- TradingView nickname: `<input>` (free-text)
- Tier: dropdown [PRO, ESSENTIALS]
- Plan: dropdown [MONTH, YEAR, LIFETIME, TRIAL]
- Trial end date: date picker — **only visible when Plan = TRIAL**
- Submit: `POST /access/classic`

### "Custom" tab
- TradingView nickname: `<input>`
- New end date: date picker (future dates only)
- Submit: `POST /access/custom`

### Error handling
- Map each error `message` directly to the toast / inline error — all four listed messages are already user-friendly (no further translation needed).
- Case-insensitive lookup: no need to lowercase client-side; send what the admin types.
- For 400 "active subscription required" (custom tab), point the admin toward the Classic tab: *"This user has no active subscription. Use the Classic tab to issue one."*

---

## Migration notes

- The old endpoint and `ChangeUserAccessDto` no longer exist. Any fetch wrapper that hit `POST /v1/admin/access` with `{isLifetime, isTrial, ...}` must be rewritten.
- The "`plan` required for paid grants" error you saw earlier is gone — ignore the earlier message that asked for a plan selector on the old endpoint.
- Audit log: admin grants do **not** create `user_transactions` rows (they're not payments). They do fire `SubscriptionChangeStatusEvent` which shows up in application logs.
