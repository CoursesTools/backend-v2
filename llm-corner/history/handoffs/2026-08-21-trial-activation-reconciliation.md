# 2026-08-21 — Trial activation reconciliation shipped and repaired prod

Author: Codex (GPT-5)
Commits: `9f8b3a6`, merge `df023cf` (PR #38)

## State of the world

PR #38 is merged and deployed. Activation snapshots now compare expiration at
PostgreSQL microsecond precision, genuine current-command mismatches become
visible DEAD jobs, and startup/five-minute reconciliation rebuilds aged
commandless PENDING activation commands. Production startup recovered all ten
affected trial subscriptions with seven fresh days. All ten are GRANTED, the
bot returned HTTP 200 for every final request, and no target retry row remains.

## Shipped

- Precision-safe activation snapshot validation and mismatch diagnostics —
  `9f8b3a6`.
- Startup/five-minute PENDING reconciliation with paid-over-trial precedence,
  fresh-seven-day trial policy, gauge, tests, docs, and DEC-003 — `9f8b3a6`.
- Production repair for subscriptions 4969–4978 — automated by startup after
  merge `df023cf`.

## Open / deferred

- GitHub's successful deployment warned that Node.js 20 actions are being
  forced onto Node.js 24 and `setup-java@v4` is deprecated. Upgrade the workflow
  actions before GitHub removes compatibility; tracked in `active/backlog.md`.
- PLAN-001's unrelated grace-period and payload-format items remain open.

## Landmines for the next session

Do not restore an old trial over a paid subscription: the reconciliation worker
deliberately chooses any non-terminated paid entitlement ahead of trials. Do
not remove the 15-minute cutoff or overwrite PENDING/DEAD ACTIVATE jobs. A
bodyless 2xx from the access bot is success. During this repair `divega4864`
received one transient HTTP 500, then the existing retry policy delivered the
same payload with HTTP 200 one second later.

## Verification record

A fresh local `gradlew.bat build --rerun-tasks` passed with 103 tests, zero
failures/errors/skips; six pre-existing Lombok/MapStruct warnings remain. PR
#38 was OPEN/CLEAN with no configured branch checks, then merged. GitHub Actions
run 32496528708 completed both build-and-push and deploy successfully. A
rollback-enforced read-only production JDBC audit before deploy found exactly
ten PENDING trials, no paid orders, no transactions, and no retry jobs for
users 5246–5256 in scope. Startup logs showed ten of ten re-staged and final
HTTP 200 delivery for all names. A second rollback-enforced SELECT confirmed
all ten subscriptions GRANTED with expirations around
`2026-08-28T15:16:22–23Z`, `target_pending_subscriptions=0`, and
`target_retry_jobs=0`. Temporary probe files were removed from local, host, and
container storage.
