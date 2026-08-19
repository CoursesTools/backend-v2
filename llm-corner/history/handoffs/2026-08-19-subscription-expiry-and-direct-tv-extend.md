# 2026-08-19 — Subscription expiry and Direct TV Extend

Author: Codex (chief+worker / chief auditor)
Commits: `775982d` plus the lifecycle-doc commit that contains this handoff
PR: https://github.com/CoursesTools/backend-v2/pull/37

## State of the world

PR #37 is open against `master`; it is not merged or deployed. Non-Stripe
payments now extend from `max(now UTC, existing expiry)` under a per-user lock,
while Stripe continues to mirror its authoritative period end. The TradingView
buffer is explicit and applies only to successful customer payments; manual,
trial, lifecycle, rename, and Direct Extend flows are exact. Expired trial and
paid scheduler paths are disjoint and revalidated under row locks.

## Shipped

- Source-aware paid expiry, serialized payment processing, and scheduler race
  protection — `775982d`.
- Explicit `EXACT` versus `CUSTOMER_PAYMENT_BUFFER` TradingView policy and
  non-compounding durable retry payloads — `775982d`, DEC-002.
- Admin-only `POST /api/v1/admin/access/direct`, returning exact delivered or
  durably queued status without mutating business records — `775982d`.
- Regression coverage for purchase and renewal bases, trial cleanup ordering,
  locks/revalidation, admin paths, TV retry/fallback, and Direct Extend —
  `775982d`.

## Open / deferred

- Operator review and merge/deploy of PR #37 →
  `active/plans/PLAN-002-subscription-expiry-and-direct-tv-extend.md` remains
  `IN REVIEW` until production verification is complete.
- After deployment, repair order #997 / subscription #4968 through the admin UI:
  first use Full subscription / Custom with `2026-09-18` to repair DB expiry and
  status, wait for delivery, then use Direct Extend with `2026-09-19` to send
  the exact exceptional TradingView boundary. This avoids raw SQL and preserves
  the intended DB-versus-bot one-day payment buffer.
- Re-establish a documented read-only production database route. The old
  `docker exec postgres psql ...` command is stale because the backend host no
  longer has a `postgres` container; tracked in
  `docs/operations/servers.md` as an operations/documentation gap.

## Landmines for the next session

Do not use Direct Extend to repair the database: by design it only sends the
bot payload and may update retry bookkeeping. Admin Classic and Custom are now
exact, so the order #997 sequence requires the separate Sep 18 database repair
followed by Sep 19 Direct Extend. Do not manually mutate stranded trials. After
deployment the broadened trial cleanup query should terminate them safely.

Read-only impact query:

```sql
SELECT status, count(*)
FROM users_subscriptions
WHERE is_trial = true
  AND expired_at <= CURRENT_TIMESTAMP
  AND status <> 'TERMINATED'
GROUP BY status
ORDER BY status;
```

Run it before deployment and after the first trial scheduler cycle; the latter
should return no rows. If the predicate behaves unexpectedly, revert PR #37
before deployment or roll back the deploy before considering a narrowly scoped
manual repair. Any manual SQL experiment must start in an explicit transaction
and end with `ROLLBACK` until its exact affected rows are independently proven.

## Verification record

Fresh-master and final `gradlew.bat build` gates passed. The implemented suite
contains 86 tests; focused coverage exercises all required payment, admin,
scheduler-order, policy, retry, and endpoint cases. The source TODO/debug/secret
audit found no new defect or credential; `git diff --check` passed apart from
Windows line-ending notices. SSH and container topology were checked read-only,
but production DB counts were not refreshed because no safe current DB access
route was available. No production writes or endpoint calls were performed, and
the real TV bot must be verified from logs after operator-controlled deployment.
