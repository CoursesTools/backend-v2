# DEC-003 — Reconcile orphaned PENDING TradingView activations

Status: ACCEPTED
Date: 2026-08-21
Author: Codex (operator requested fresh trial recovery)

## Context

TradingView activation events are delivered asynchronously after the database
transaction commits. A process interruption can therefore leave a committed
PENDING subscription without a runnable event. In the August 2026 incident,
PostgreSQL's microsecond timestamp precision also changed freshly generated
nanosecond expiration values during persistence; exact snapshot comparison
then classified the matching event as stale and deleted its durable command.
The subscription remained PENDING with neither a bot request nor a retry row.

## Options considered

1. Remove snapshot validation — rejected because an older event could overwrite
   a newer admin/payment command at the TradingView bot.
2. Re-send every PENDING subscription on every scheduler tick — rejected
   because it races legitimate in-flight delivery and can overwrite a visible
   retry or DEAD command.
3. **CHOSEN: compare timestamps at database precision and reconcile only aged,
   commandless PENDING subscriptions.**

## Decision

Generated subscription timestamps are canonicalized to PostgreSQL's
microsecond precision, and listener comparison allows at most one microsecond
of storage difference. A matching current command with any other snapshot
mismatch is moved to DEAD with field-level diagnostics instead of disappearing.

At startup and every five minutes, subscriptions PENDING for at least 15
minutes are revalidated under the established user -> subscription -> ACTIVATE
lock order. Existing PENDING and DEAD commands are never overwritten. A paid
entitlement supersedes a trial. A current commandless trial is restarted with
the operator-selected full seven fresh days; a paid subscription preserves its
database expiration and customer-payment buffer. Manual grants remain exact.

## Consequences

Lost after-commit events and the known precision incident self-heal without raw
SQL or replaying payment/email side effects. Operators can monitor
`subscriptions.stuck_pending.count` and investigate genuine snapshot conflicts
in the TV retry admin page. Recovery may send a duplicate only when the remote
bot accepted an earlier request but the application lost every local success
and retry signal; the bot's grant operation must therefore remain idempotent
for the same user and expiration.
