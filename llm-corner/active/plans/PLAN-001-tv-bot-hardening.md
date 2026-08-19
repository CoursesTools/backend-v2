# PLAN-001 — TV-bot hardening follow-ups (deferred items)

**Status:** DRAFT
**Owner:** chief agent (unassigned — needs operator prioritization)
**Commit cadence:** TODO(operator): agree before starting — each item is independently shippable, so "one item = one commit/PR" is the natural split. ANY push to master auto-deploys prod.

## 0. TL;DR

The urgent TV-bot fixes shipped across two PRs: **PR #33** (`ea8ec83`, "+1 day TradingView bot access buffer + failure diagnostics") shipped the +1d expiry pad + bot-rejection diagnostics; **PR #34** shipped the dead retry-config retirement + the 404 fallback-overload surfacing. Three follow-ups were consciously deferred: (1) send the bot an unambiguous timestamp so the pad can shrink, (4) cover the Stripe dunning window (bot cuts access at expiredAt+1d while DB grace is 7d), and (5) a reconciler for subscriptions stuck in `PENDING` when the async activation event is lost. Plus one small deferred cleanup: the withdrawal `@Retry` that never applies.

## 1. Context

How TV access is granted today (all verified against current code):

- The bot payload's `expiration` is a **naive `LocalDateTime`** serialized as `ISO_LOCAL_DATE_TIME` — no zone/offset — by the app-wide ObjectMapper (`dto/external/ActivateTradingViewAccessDto.java:37-43`, `config/ApplicationConfiguration.java:42-44`).
- Successful customer payments are padded by `CUSTOMER_PAYMENT_EXPIRY_BUFFER_DAYS = 1` in `customerPaymentGrant()`; every non-payment flow is exact (DEC-002). There is still **no bot-revoke channel**.
- Bot endpoints (`.env:25-27`): activate `http://45.141.184.24:4320/open`, rename `/username_changer`, withdrawal `/withdrawal`.
- Activation is async: `SubscriptionChangeStatusListener.activateUserSubscription` (`@TransactionalEventListener @Async`, REQUIRES_NEW) fires on CREATED / TRIAL_CREATED / EXTENDED / RESTORED, calls the bot, then flips the sub from `PENDING` to `GRANTED` (`listener/SubscriptionChangeStatusListener.java:31-36,50-78`).
- Transient bot failures fall back to a durable retry queue (`trading_view_retry_jobs`, polled every minute per `configs/scheduler.yml:7`; 10 attempts, backoff up to 86400s per `application.yml:106-109`).
- DB grace period is 7 days (`SubscriptionService.GRACE_PERIOD_DAYS`, `service/SubscriptionService.java:74`). Expiry cron (hourly) moves expired `GRANTED` subs to `GRACE_PERIOD` and publishes `GRACE_PERIOD_START` (`service/SubscriptionDeactivationService.java:30-43`) — an event type the activation listener ignores.

### Just-done context (shipped via PR #34, do not re-plan)

- **(2) Dead-config retirement — DONE.** `@Retry(name="client-error-included")` pointed at a resilience4j config with no `instances` binding, so it silently resolved to `default`. The annotation now says `default` and the orphaned config is gone — only `resilience4j.retry.configs.default` exists (`application.yml:59-70`; rationale comment at `service/user/UserTransactionService.java:98-103`). Behavior unchanged by design.
- **(3) 404 fallback-overload surfacing — DONE.** resilience4j fallbacks catch every Throwable, so the ignore-listed `TradingViewUserNotFoundException` (bot 404 = nickname doesn't exist) was being swallowed and mis-enqueued as a transient retry. Type-specific fallback overloads now rethrow the 404 to the caller (`service/external/ActivatingSubscriptionService.java:89-107`), and non-2xx bot responses log status + body (`ActivatingSubscriptionService.java:38-47`). The async listener turns a permanent 404 into a DEAD retry row for the admin page while still granting DB access (`SubscriptionChangeStatusListener.java:63-75`).

## 2. Design — deferred items

### Item 1 — unambiguous timestamp to the bot (needs bot-owner coordination)

- **Problem:** `expiration` is offset-less. Backend writes UTC; the bot runs on Moscow-time infrastructure and can interpret the value as local time, cutting access up to ~3h early (`ActivateTradingViewAccessDto.java:20-30`). The +1d pad exists largely to mask this.
- **Why it matters:** the payment-only pad is still a full day. An unambiguous wire format lets it shrink to a few hours without risking paid access.
- **Proposed fix:** agree a format with the bot owner — epoch seconds or ISO-8601 with explicit offset (`...Z`). Backend side: change the field type/serializer on both TV DTOs only (keep the app-wide ObjectMapper untouched). Then reduce `CUSTOMER_PAYMENT_EXPIRY_BUFFER_DAYS` to an hours-scale pad.
- **Risk:** (a) coordinated deploy — the bot must accept the new format before backend ships; (b) stored retry payloads in `trading_view_retry_jobs.payload` replay the OLD format, so deserialization must stay backward-compatible (a strict `@JsonFormat` was already tried and reverted before merge for exactly this reason — see `ActivateTradingViewAccessDto.java:37-42` and the `docs/reference/gotchas.md` entry); (c) shrinking the pad before confirming the bot's parsing = early cutoffs for paying users.

### Item 4 — Stripe dunning window vs bot cutoff (decision needed)

- **Problem:** when a Stripe renewal charge fails, Stripe's dunning/smart-retries can span days-to-weeks (dashboard-configured; exact window: TODO(operator) — check Stripe dashboard retry settings). The backend keeps the sub in `GRACE_PERIOD` for 7 days, but the bot was last told `expiredAt + 1d` — so the user loses indicators on day 2 of dunning even if the charge recovers on day 3. Recovery works DB-side (`invoice.payment_succeeded` → GRACE_PERIOD branch → RESTORED → bot re-grant, `SubscriptionService.java:200-202`), but the days in between are dark.
- **Why it matters:** paying customers in a normal card-retry cycle silently lose the product they will end up paying for — worst-case churn driver.
- **Proposed fix (preferred):** on `GRACE_PERIOD_START`, push a bot grant with `expiration = expiredAt + GRACE_PERIOD_DAYS` — as a **separate listener handler**, NOT by adding `GRACE_PERIOD_START` to `EVENTS_FOR_ACTIVATE` (the existing handler ends by setting status `GRANTED`, `SubscriptionChangeStatusListener.java:76`, which would corrupt the grace state). Alternatives: (b) grace-cover only Stripe-backed non-trial subs (dunning is a Stripe concept; `paymentMethod` is on the sub); (c) handle `invoice.payment_failed` webhooks to grant only when dunning is genuinely active — most precise, most work (webhook handler currently only processes `invoice.payment_succeeded` + `customer.subscription.updated/deleted`, `StripePaymentService.java:58-59,227-231`).
- **Risk:** every expired sub (including deliberate cancellations) keeps bot access for the full 7-day grace — free-access expansion with no revoke channel to claw it back. This is a product/revenue call: TODO(operator) — decide grace-cover-all vs Stripe-only vs payment_failed-triggered.

### Item 5 — reconciler for subscriptions stuck in PENDING

- **Problem:** `GRANTED` is only ever set by the async listener after commit. The Spring event is in-memory: if the app restarts/crashes between the producing commit (payment webhook, trial creation) and the async listener run, the event is gone and the sub stays `PENDING` forever. No scheduler touches `PENDING` — the expiry cron only queries `GRANTED` (`SubscriptionService.java:155-157`); `SubscriptionScheduler` covers expiry/trial/past-grace only. A pre-existing TODO asks for exactly this job (`scheduler/SubscriptionScheduler.java:18`, in Russian: "make a job that grants accesses to those it didn't").
- **Why it matters:** a paid customer with no access and no self-healing path; today it's found only via support tickets.
- **Proposed fix:** scheduled reconciler that finds `PENDING` subs with `updatedAt` older than a cutoff (~15 min; `updatedAt` exists via `Auditable`, `entity/base/Auditable.java:25-29`), re-runs the activation flow (bot grant via the existing `@Retry` + durable-queue machinery), and sets `GRANTED`. Mirror the existing `SubscriptionStateReconciliationService` pattern including its Micrometer gauge (`subscriptions.past_grace_period.count` precedent, `service/SubscriptionStateReconciliationService.java:22-38`) with a `subscriptions.stuck_pending.count` gauge so Grafana alerts before users do.
- **Risk:** (a) cutoff too short re-activates subs the normal async path is still processing (double bot call — the bot `/open` endpoint appears idempotent for the same payload, but verify with bot owner); (b) must exclude `PENDING` rows superseded by a newer subscription for the same user; (c) deliberately skip = do NOT blindly re-fire emails — reconcile access only.

### Deferred cleanup — withdrawal `@Retry` never applies

- **Problem:** `requestWithdrawal` is `private` and self-invoked from `withdraw()` in the same class (`service/user/UserTransactionService.java:92,104-105`). Spring AOP proxies neither intercept self-invocation nor private methods, so the `@Retry` + its `handleFallback` have never run. Actual behavior: single attempt; a bot failure propagates raw and rolls back the `@Transactional` balance deduction (which is at least atomic).
- **Why it matters:** dead annotation misleads readers into believing withdrawals retry; the intended friendly `ExternalServiceException` never fires.
- **Proposed fix:** decide the semantics FIRST — auto-retrying a money-moving POST (`/withdrawal` on the TV-bot host) is dangerous without an idempotency key (double payout). Likely correct end-state: extract the HTTP call to a separate bean so the aspect applies, retry only on connect-level failures, or delete the annotation and keep single-attempt + explicit error mapping.
- **Risk:** enabling retries naively can double-pay a partner withdrawal. Needs bot-owner confirmation of `/withdrawal` idempotency before any retry is real.

## 3. Phases

### Phase A — Item 5: PENDING reconciler (no external coordination; do first)
- [ ] Repo query + reconciler service (`stuck_pending` gauge, cutoff-aged `PENDING` scan, re-grant, set `GRANTED`) modeled on `SubscriptionStateReconciliationService`
- [ ] Cron entry in `configs/scheduler.yml`; tests for cutoff/supersession edge cases
- Files: `service/`, `scheduler/`, `repository/user/UserSubscriptionRepository.java`, `configs/scheduler.yml`
- Verify: kill app between webhook commit and listener; confirm reconciler grants on next tick; gauge visible in Grafana (ct-logs Prometheus :9090)

### Phase B — Item 4: dunning decision + grace-window grant
- [ ] TODO(operator): decide variant (all-grace vs Stripe-only vs `invoice.payment_failed`) and read the live Stripe dashboard retry schedule
- [ ] Implement chosen variant as a new listener handler (no status mutation)
- Files: `listener/SubscriptionChangeStatusListener.java` (or new listener), possibly `StripePaymentService`
- Verify: Stripe test-clock dunning cycle; bot receives grace-window expiration; DB status stays `GRACE_PERIOD`

### Phase C — Item 1: unambiguous timestamp (blocked on bot owner)
- [ ] Agree wire format + rollout order with bot owner (bot at 45.141.184.24:4320)
- [ ] DTO-local serializer change, backward-compatible deserialization for stored retry payloads
- [ ] Shrink `CUSTOMER_PAYMENT_EXPIRY_BUFFER_DAYS` only after prod verification of bot parsing
- Verify: prod log line `TV activation succeeded ... expiration=` shows new format; replay one legacy retry row

### Phase D — withdrawal retry cleanup (small, independent)
- [ ] Confirm `/withdrawal` idempotency with bot owner; then extract-to-bean or delete annotation
- Verify: unit test proving the chosen semantics actually execute through the proxy

## 4. Subagent split

Solo session per phase — items touch overlapping subscription/listener files; parallelizing invites merge pain for no gain.

## 5. Risks / rollback

One item = one commit; every push to master auto-deploys, so each phase must be independently revertible. Phase A is pure-additive (new scheduler) — revert = delete. Phase B expands free access irreversibly for the grace window it ran (no revoke channel). Phase C requires bot-side rollback coordination; keep the old format deserializable so a backend revert is always safe.

## 6. Doc impact

Update `docs/architecture/` subscription-lifecycle doc (bot-expiry semantics, new reconciler) and `docs/operations/log-playbook.md` (new gauge + reconciler log lines) in the same commits.

---
On completion: set final Status, run the session-close checklist (`../protocols/lifecycle.md`) — archive to `../history/plans/` + INDEX row; deferred items → `../active/backlog.md`.
