# 2026-08-19 — TV activation ordering follow-up

Author: Codex (chief+worker / chief auditor)
Commits: `d4b10a2`, `8807597`
PR: https://github.com/CoursesTools/backend-v2/pull/37

## State of the world

PR #37 remains open, unmerged, and undeployed. Chief audit found that an old
async payment event could re-read newer admin state or run after Direct Extend.
Activation events now carry immutable payload snapshots and transactionally
stage a command-tokened latest ACTIVATE row before commit. Delivery locks the
subscription then that row; superseded commands never reach the bot.

## Shipped

- Nullable retry-job command token migration — `d4b10a2`.
- Atomic latest-command staging shared by payment/admin/Direct/synchronous
  activation, snapshot validation, locked delivery, and stale-event rejection
  — `8807597`.
- Safe stale payment status reconciliation: matching PENDING state becomes
  GRANTED without sending the superseded payload; GRACE/TERMINATED is never
  revived — `8807597`.
- Permanent activation 404 converts the staged PENDING row to DEAD, while
  transient retry retains the already-final exact/buffered payload — `8807597`.

## Open / deferred

- PLAN-002 correctly remains `IN REVIEW` until operator merge/deploy and the
  production verification/order #997 repair recorded in the prior handoff.
- Activation ordering is unified for ACTIVATE commands. TradingView username
  RENAME remains its existing separate command type and contract; this follow-up
  did not change rename semantics.

## Landmines for the next session

Preserve producer/listener lock order: subscription row first, ACTIVATE retry
row second. Reversing it can deadlock a producer that mutated the subscription
and is waiting to stage. Do not move staging out of the producer transaction:
the command token must exist before the async after-commit listener can run.

## Verification record

Fresh `gradlew.bat build --rerun-tasks` passed in 12 seconds: 94 tests, zero
failures/errors/skips. Regression coverage runs old payment after newer admin,
old admin after newer payment, payment superseded by Direct (only status is
reconciled), GRACE non-resurrection, permanent 404 DEAD conversion, atomic
staging, and command-ID rejection. Source audit found only the five documented
legacy TODOs and no debug output or hardcoded secret. No production call or
write was performed.
