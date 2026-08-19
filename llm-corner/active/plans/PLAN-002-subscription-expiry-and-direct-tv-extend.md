# PLAN-002 — Repair subscription expiry and add Direct TV Extend

**Status:** IN REVIEW
**Owner:** Codex chief+worker
**Commit cadence:** one cohesive incident-fix commit plus lifecycle handoff;
operator pre-authorized commit, push, and PR but not merge.

## 0. TL;DR

Fix stale-expiry arithmetic that sent order #997 backward, make paid/trial
expiry schedulers disjoint, restrict the TV `+1 day` buffer to customer
payments, and expose an admin-only bot synchronization endpoint that never
changes the business subscription.

## 1. Context

Order #997 converted an already-expired trial by adding the monthly duration
to the trial's July expiry. The global DTO buffer then produced an August 16
bot expiration, and the paid scheduler moved the new row to grace. Scheduler
queries could also strand expired trials in PENDING or GRACE_PERIOD.

## 2. Design

- Non-Stripe purchases use `max(now UTC, existing expiry) + duration`.
- Payment callbacks lock the user before reading/updating the subscription.
- Paid and trial scheduler queries are mutually exclusive; paid candidates
  are locked and revalidated before state transition.
- DEC-002 makes exact-vs-payment-buffer explicit at the event boundary.
- Activation events snapshot their final payload and transactionally replace a
  command-tokened per-user ACTIVATE outbox slot. Locked delivery rejects
  superseded events, so async execution order cannot mix policies or overwrite
  a newer Direct/admin/payment command.
- Direct Extend inherits identity/tier/lifetime and may only touch TV retry
  bookkeeping, never the subscription/order/transaction tables.

## 3. Phases

### Phase A — expiry and scheduler safety
- [x] Safe non-Stripe base and Stripe-exact preservation
- [x] Per-user payment serialization and callback idempotency coverage
- [x] Disjoint paid/trial queries and both-order regression coverage

### Phase B — TV policy and Direct Extend
- [x] Explicit event policy and exact/payment DTO factories
- [x] Admin endpoint, validation, audit log, durable delivery outcome
- [x] Manual/trial/custom/rename/lifetime/retry regression coverage
- [x] Chief-auditor follow-up: deterministic async/Direct command ordering,
  snapshot validation, PENDING-status reconciliation, and 404 DEAD conversion

### Phase C — delivery
- [x] Full local build and chief-auditor source review
- [ ] Push PR and await operator-controlled merge/deploy
- [ ] Verify scheduler cleanup, order #997 repair, and Direct Extend in prod

## 4. Subagent split

Solo Codex chief+worker session; subscription, event, listener, and tests are
too coupled for safe parallel edits.

## 5. Risks / rollback

Revert the feature commit before merge. No migration is included. Existing
expired trials will be terminated by the first trial scheduler tick; inspect
the impact query in the handoff before deployment. Order #997 still requires
an operator repair after deployment.

## 6. Doc impact

Contracts, subscription/TV, payments, admin, persistence/schedulers,
ecosystem contracts, gotchas, DEC-002, changelog, and handoff.
