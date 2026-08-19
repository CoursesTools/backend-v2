# MSG-2026-08-19 — Fix subscription expiry and add Direct TV Extend
Created: 2026-08-19
From: admin-web Codex (operator-directed)
To: backend Codex
Response path: ct-projects/admin-web/claude-msgs/
Context: Production order #997 investigation; operator decisions confirmed 2026-08-19

## Authority and delivery

The operator explicitly authorizes you in this task to implement the complete
backend plan below, create a dedicated fix branch from current `master`, make
atomic commits, push the branch, and open a PR to `master`. Do not push directly
to or merge `master`; reply with the branch, commits, PR, test evidence, and any
remaining production action. Suggested branch:
`fix/subscription-expiry-and-direct-tv-extend`.

Before work, gain full context through `llm-corner/README.md`, read your Codex
corner, the subscription/TV, payment, admin, persistence/event architecture
docs, gotchas, and this entire message. Run `./gradlew build` before editing and
again before every commit/push. Treat this as a production payment/access
incident and perform your chief-auditor review on the final diff.

## Incident evidence

CryptoCloud order #997 was paid on 2026-08-19 for
`aryansn484@gmail.com` / TV user `aryansn484`, Essentials Month, $14.90.
The bot received:

```text
expiration=2026-08-16T15:52:18.907923
```

The expected bot expiration was 2026-09-19. Investigation found that the old
trial had expired on 2026-07-16, but paid-subscription creation unconditionally
used that stale trial expiry as its base. It produced a DB expiry around
2026-08-15; the global bot `+1 day` factory then produced August 16. The next
hourly scheduler moved the newly paid row into grace.

The scheduler paths are also not mutually exclusive: the generic expired-sub
query includes trials, while the trial cleanup query only sees `GRANTED` rows.
Depending on hourly listener order, the generic job can move an expired trial
to `GRACE_PERIOD`, after which trial cleanup and post-grace reconciliation both
ignore it. The read-only production investigation found hundreds of expired
trials stranded in `GRACE_PERIOD` and several in `PENDING`. Re-verify current
code and data; do not trust these counts as timeless facts.

## Required behavior 1 — correct paid subscription base

For non-Stripe successful payments that create, replace, extend, or restore a
paid subscription, compute the duration from a safe base:

```text
base = max(now, existing expiration)
new DB expiration = base + purchased plan duration
```

An expired trial or expired paid row must never pull a new purchase backward
into the past. An active, future-dated subscription should retain its remaining
time when extended. Preserve Stripe-owned exact `current_period_end` semantics;
do not replace Stripe's authoritative period boundary with backend arithmetic.
Preserve lifetime behavior.

Audit transaction boundaries and concurrent/double delivery. Use repository
locking or equivalent serialization and revalidation where required so two
payment callbacks cannot both extend from the same stale expiry. Preserve the
existing payment idempotency guarantees.

## Required behavior 2 — make expiry schedulers disjoint

The paid/generic expiration flow must explicitly exclude trials. The trial
expiration flow must explicitly select trials and all states that require
cleanup, rather than depending on an ordering accident between hourly jobs.
Past-grace reconciliation must remain safe for paid subscriptions and lifetime
rows.

Add regression coverage that runs the scheduler paths in either order and
proves:

- an expired trial reaches the intended terminal state;
- it cannot be stranded in `GRACE_PERIOD` or `PENDING` by the paid scheduler;
- paid subscriptions still enter and leave grace correctly;
- lifetime subscriptions are untouched;
- active and expired trials followed by a purchase produce future paid expiry;
- early paid renewal extends from the future expiry;
- duplicate/concurrent callbacks do not double-extend.

For already-stranded production trials, propose a narrowly scoped one-time
repair with a read-only impact query and rollback/verification instructions.
Do not mutate production manually under this assignment. If a Flyway cleanup
is justified, add a new immutable migration only after proving its predicate
cannot touch paid or lifetime rows.

## Required behavior 3 — `+1 day` only for customer payments

Operator decision:

- Every successful customer payment that creates, extends, or restores paid
  access, including paid renewals, sends `DB expiration + 1 day` to the TV bot.
- Trials, admin Classic grants, admin Custom updates, Direct Extend, username
  changes, lifecycle-only synchronization, and every other non-payment action
  send the exact DB/admin-selected expiration with no added day.
- Lifetime remains lifetime and is not arithmetically padded.

Do not infer “paid action” from plan or `paymentMethod`: admin Classic
MONTH/YEAR writes a paid-looking MANUAL subscription but must not get the day.
Carry an explicit source/policy through the event boundary or use separate
type-safe factories so every call site states whether the activation came from
a successful customer payment. Avoid a default that silently reintroduces a
global buffer. The final expiration stored in a TV retry payload must be replayed
unchanged; retries must never compound the day.

Update `ChangeTradingViewNameDto` and every direct DTO factory call site so
non-payment flows are exact. Add focused tests for first purchase, renewal,
grace restoration after payment, trial, admin Classic MONTH/YEAR/TRIAL,
admin Custom, Direct Extend, rename, lifetime, and retry replay.

## Required behavior 4 — admin Direct Extend endpoint

Add an admin-only endpoint for the admin-web Custom sub-mode:

```http
POST /api/v1/admin/access/direct
Content-Type: application/json

{
  "tradingViewName": "aryansn484",
  "expiredAt": "2026-09-19"
}
```

Contract and safety rules:

- Require an existing non-terminated subscription. If none exists, return a
  clear 400 explaining that Direct Extend requires an existing subscription.
- Resolve the user case-insensitively and inherit email, tier, and lifetime
  state from the existing subscription. The client must not provide them.
- Send the exact selected expiration to the TV access bot with no `+1 day`.
- Do not change any user, subscription, order, or transaction record and do not
  publish a subscription status event. In particular, never change the
  subscription expiry or status to `GRANTED`.
- Reuse the durable TV activation/retry mechanism. A transient failure may
  create/update its retry bookkeeping; that is allowed and must not mutate the
  business subscription. A permanent nickname 404 must remain a friendly
  operator-facing error.
- A successful/accepted response should mean “submitted to TradingView”, not
  claim the database was updated. Document the exact response shape for
  admin-web and ensure OpenAPI exposes it.
- Log an auditable admin action without secrets: actor if available, target
  user/subscription id, requested expiration, and delivered-vs-queued outcome.

The endpoint is for exceptional synchronization only. It must not become a
backdoor that invents tier/access for a user without a subscription.

## Required documentation and proof

Update the authoritative subscription/TV, admin API, persistence/scheduler,
contracts/gotchas, and changelog material affected by the implementation. If
the solution introduces a cross-cutting source/policy concept, record the WHY
in a DEC.

At minimum, provide:

1. Targeted tests for every behavior above.
2. A final clean `./gradlew build`.
3. A source-only TODO/debug/secret audit per the audit gate.
4. Final diff review with file/line evidence and explicit confirmation that
   manual/admin paths are unbuffered.
5. A PR with Summary / Why / Test plan and production verification steps.
6. A reply to the declared response path with commits, PR URL, test output,
   any Medium+ finding, and the exact operator-only repair still needed for
   order #997 / its subscription after deployment.

Do not declare completion if the endpoint is merely compiled but unwired, or
if the bot payload policy is only changed in one of its call paths.
