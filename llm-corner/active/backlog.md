# Backlog — Known Open Items

Deferred work, accepted audit findings, and confirmed TODO/FIXMEs
that have no active plan. One line each, severity-tagged, with
source. Items leave this list by: shipping (note the commit),
becoming a PLAN-NNN, or being explicitly dropped (note why).

The session-close checklist (`../protocols/lifecycle.md`) feeds
this file; don't let findings live only inside audit reports.

## Open

<!-- Severity: [Critical] [High] [Medium] [Low] [Process]
Format:
- **[Severity]** <one-line description>. Source: <where it came from>.
-->

- **[Medium]** `OrderScheduler` is an empty stub — its legacy TODO asks
  for cleanup of orders never paid within some window, so unpaid orders
  currently accumulate forever (`scheduler/OrderScheduler.java:10`).
  Source: repo TODO sweep at corner bootstrap, 2026-07-12.
- **[Low]** Four more legacy Russian-language TODOs in prod code predate
  the no-TODO convention: user-count scaling watch
  (`scheduler/SubscriptionScheduler.java:17`), balance-payment redirect
  link (`service/payment/impl/BalancePaymentService.java:31`),
  pre-expiry reminder emails at 3/7 days
  (`service/SubscriptionDeactivationService.java:42`). (The fifth,
  `SubscriptionScheduler.java:18`, became PLAN-001 item 5.) Source:
  repo TODO sweep at corner bootstrap, 2026-07-12.
- **[Process]** `agents/claude/` and `agents/codex/` workspaces have not
  been instantiated from `agents/_template/` yet, so the inbox paths in
  `../protocols/messaging.md` don't exist on disk. Source: corner
  bootstrap finalization, 2026-07-12.

## How items leave this list

- **Shipped:** struck through with the commit SHA, then deleted at
  the next session-close sweep.
- **Promoted to plan:** moved to `active/plans/PLAN-NNN-<slug>.md`.
- **Dropped:** struck through with a one-line "why dropped",
  deleted at next sweep.
