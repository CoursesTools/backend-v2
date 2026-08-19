# DEC-002 — TradingView expiration policy is explicit at the event boundary

Status: ACCEPTED
Date: 2026-08-19
Author: Codex (operator requested and confirmed)

## Context

The original one-day TradingView safety buffer lived in a DTO factory and
therefore applied to every activation and rename. That protected paying users
from the bot's offset-less timestamp and slightly late renewal callbacks, but
also extended trials and admin-selected expirations. Payment events and admin
Classic MONTH/YEAR grants share lifecycle event types and paid-looking plan
data, so neither event type nor payment method can safely identify a real
customer payment.

## Options considered

1. Infer payment origin from event type, plan, or payment method — rejected:
   admin grants deliberately reuse those values and would still receive the
   buffer.
2. Keep a globally buffered factory and subtract a day in manual paths —
   rejected: compensation is fragile and stored retry payloads make repeated
   transformations dangerous.
3. **CHOSEN: carry an explicit expiration policy on every subscription event**
   — successful customer payments select `CUSTOMER_PAYMENT_BUFFER`; every
   other source selects `EXACT`. Separate DTO factories apply the selected
   policy once before durable retry serialization.

## Consequences

Every event publisher must state the policy, so a new call site cannot compile
without making the business decision. Retry rows store the final expiration
and replay it unchanged. Customer-payment first purchases, renewals, and
restorations keep the one-day protection; trials, admin actions, lifecycle
syncs, Direct Extend, and renames match the database/operator date exactly.
Lifetime stays unbuffered under either policy.

The policy and all payload-relevant values are snapshotted into the event.
Before publishing, activation-producing transactions stage that final DTO in
the single PENDING ACTIVATE retry slot with a fresh command ID. Async delivery
locks the slot and sends only if the ID still matches; Direct Extend and
synchronous grants use the same slot. This is required because re-reading the
subscription in an async listener can combine an old policy with newer state,
and snapshotting alone cannot prevent an old event from arriving after a newer
Direct command. Row-lock serialization guarantees that, whichever delivery
starts first, the newest staged command is the final bot write.
