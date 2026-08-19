# Gotchas — Pinned Invariants & Sharp Edges

Cross-agent sharp edges. Each entry burned someone at least once — match the
**symptom** first; the fix is usually not where the symptom points. Add new
entries when a root cause turns out to be non-obvious; keep entries even when a
regression test pins them (the test stops the regression, this file stops the
wasted investigation). Format per entry: **symptom → root cause → fix**, with a
code pointer.

---

## Deploy & config

### ANY push to master auto-redeploys the live payment backend

**Symptom:** you push a "harmless" commit to master and production restarts minutes later.
**Root cause:** `.github/workflows/docker-build.yml` triggers `on: push: branches: [master]` (lines 3–5): gradle build → push image → SSH to the prod host → `docker compose pull backend && docker compose up -d backend` (lines 56–71). There is no manual gate.
**Fix:** treat master as prod. Do risky work on a branch; merge only when you intend to deploy. `workflow_dispatch` exists for manual redeploys.

### A resilience4j config under `configs:` with no `instances:` binding is a silent no-op

**Symptom:** you point `@Retry(name = "my-config")` at a config defined under `resilience4j.retry.configs:` and the retry/ignore behavior doesn't change at all.
**Root cause:** annotation names bind to `resilience4j.retry.instances`, not `configs`. With no matching instance, the annotation silently falls back to the `default` config. The retired `client-error-included` config was exactly this trap — declared under `configs` with no `instances` binding, so `@Retry(name="client-error-included")` silently used `default` (verified a no-op at runtime). It was deleted and its user pointed at `default` in PR #34.
**Fix:** either add an `instances:` entry (optionally `baseConfig: my-config`) or name the annotation `default` honestly. Post-mortem comment: `service/user/UserTransactionService.java:98-104`. Current config has only `configs.default` (`application.yml:59-70`).

### `@Retry` on a private / self-invoked method never applies

**Symptom:** an annotated method gets zero retries and its `fallbackMethod` never fires.
**Root cause:** Spring AOP proxies only intercept external calls to public methods. `UserTransactionService.requestWithdrawal` (`UserTransactionService.java:104-105`) is `private` and called from `withdraw()` in the same class — the aspect never applies (documented as a known-deferred issue in the comment above it).
**Fix:** put `@Retry` on a public method invoked through the Spring proxy (from another bean), like the four external services do (`ActivatingSubscriptionService.java:29`, `GeoLocationService.java:21`, `OAuthGoogleService.java:33`, `OAuthDiscordService.java:33`).

### resilience4j `fallbackMethod` catches ALL throwables — including ignore-listed ones

**Symptom:** an exception on the retry config's `ignore-exceptions` list (e.g. the 404 `TradingViewUserNotFoundException`) never reaches the caller; instead the generic fallback runs and mis-handles it.
**Root cause:** `ignore-exceptions` only stops *re-attempts*; the fallback still swallows every throwable. A permanent "nickname doesn't exist" 404 was being enqueued as a transient PENDING retry instead of surfacing to the caller.
**Fix:** add a fallback *overload* typed to the exception that must propagate — resilience4j dispatches to the most specific overload — and rethrow from it. See `ActivatingSubscriptionService.java:89-107`.

## JSON serialization (TV bot wire contract + retry queue)

### Field `@JsonFormat(pattern=...)` also constrains DESERIALIZATION → poisons stored payloads

**Symptom:** after "stabilizing" a DTO's date format, every replay of previously stored JSON throws `InvalidFormatException`; retry-queue rows rot to DEAD and even admin force-retry can't recover them.
**Root cause:** `@JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss")` on `expiration` contextualizes the JSR-310 *deserializer* too, so legacy `trading_view_retry_jobs.payload` rows written with fractional seconds (the normal `now()`-derived shape) stop parsing. Tried during PR #33 and reverted before merge — it never shipped to master.
**Fix:** never put a strict `@JsonFormat` on a DTO that is persisted-and-replayed (both TV bot DTOs carry a warning comment: `dto/external/ActivateTradingViewAccessDto.java:37-42`). Regression tests pin it: `ActivateTradingViewAccessDtoTest.deserializesLegacyFractionalSecondPayload` (test lines 59–68) exercises the real `ApplicationConfiguration().objectMapper()`.

### `ISO_LOCAL_DATE_TIME` output shape is value-dependent

**Symptom:** the same field serializes as `...T02:46:35.991576` (fractional), `...T15:41:38` (whole) or `...T15:41` (seconds dropped when `:00`) depending on the value — confusing log/DB diffs and any consumer doing strict parsing.
**Root cause:** the app-wide ObjectMapper registers `LocalDateTimeSerializer(DateTimeFormatter.ISO_LOCAL_DATE_TIME)` (`config/ApplicationConfiguration.java:43-44`); that formatter emits nanos when nonzero and omits seconds when zero.
**Fix:** accept it — the TV bot accepts all three forms (verified in prod). Do NOT "fix" it with `@JsonFormat` (see previous entry). Consumers of our JSON must parse leniently.

### Lombok `private boolean isLifetime` serializes as JSON key `"lifetime"`, not `"isLifetime"`

**Symptom:** the field you named `isX` appears in the wire JSON as `"x"`; a payload written with key `"isX"` deserializes as `false`.
**Root cause:** Lombok generates getter `isLifetime()` / setter `setLifetime()`, so the Jackson bean property is `lifetime`. Pinned by `ActivateTradingViewAccessDtoTest.wireContract_keysUnchanged` (test line 42). Second bite: migration `V14__fix_lifetime_expiry_year.sql` patched stored retry payloads with the key `isLifetime`, which Jackson ignores (`@JsonIgnoreProperties(ignoreUnknown=true)`) — those rows deserialize with `isLifetime=false` and work only because the patched year-2100 expiration is itself bot-valid.
**Fix:** when the wire key matters, pin it with `@JsonProperty` (as done for `"tv"`, `"old"`, `"new"`) and add a wire-contract test before renaming anything.

### Retry-queue DTOs must tolerate unknown fields

**Symptom:** adding a field to a TV bot DTO makes previously stored `trading_view_retry_jobs.payload` rows (or a rolled-back binary reading newer rows) fail to deserialize.
**Root cause:** payloads outlive the code that wrote them.
**Fix:** both DTOs carry `@JsonIgnoreProperties(ignoreUnknown = true)` (`ActivateTradingViewAccessDto.java:14`, `ChangeTradingViewNameDto.java`) — keep it on anything persisted as JSON. Same principle for vendor webhooks: `CryptoRetrieveDto` ignores unknown CryptoCloud fields.

## TradingView bot expiry

### TV access revoked ~3h before the Stripe auto-charge

**Symptom:** paying users lose indicator access a few hours before their renewal charge lands.
**Root cause:** the bot receives a naive, offset-less timestamp. The value is UTC, but the bot runs on Moscow-time infra (bot at 45.141.184.24) and reads it as MSK → revokes ~3h early; Stripe's renewal webhook can also land slightly after the period boundary.
**Fix:** successful customer-payment activations use
`ActivateTradingViewAccessDto.customerPaymentGrant()` and receive the one-day
buffer. Every non-payment path uses `exactGrant()` (rename is exact too).
`SubscriptionChangeStatusEvent.tradingViewExpirationPolicy` makes the source
explicit because admin MONTH/YEAR grants otherwise look paid. The final value
is persisted to the retry queue so replay never compounds it. See DEC-002.

### Expired trials stranded in PENDING or GRACE_PERIOD

**Symptom:** an expired trial remains non-terminated indefinitely; scheduler
order can move it to GRACE_PERIOD and the trial cleanup never sees it.
**Root cause:** the generic expired query included trials, while the trial
query selected only GRANTED. Both hourly jobs run on the same cron.
**Fix:** the paid query now requires `isTrial=false`; trial cleanup selects
every expired `status <> TERMINATED` trial. Paid candidates are also locked and
revalidated so a concurrent renewal cannot be overwritten by stale scheduler
selection.

### TradingView rejects year-9999 expiration dates

**Symptom:** lifetime grants fail at the bot; everything else works.
**Root cause:** the old lifetime sentinel `9999-12-31` is too far in the future for TradingView's API.
**Fix:** sentinel is `LIFETIME_EXPIRY = 2100-12-31 23:59:59` (`SubscriptionService.java:75`) plus an explicit `isLifetime` flag in both bot DTOs (buffer skipped for lifetime). `V14__fix_lifetime_expiry_year.sql` migrated DB rows and stored retry payloads. Commit `a69cf29`.

### Bot 404 (nickname not found) is permanent — route it by caller, don't retry it

**Symptom:** a retry row for a misspelled TradingView nickname sits PENDING forever, or an admin grant returns 503.
**Root cause:** "user not found" cannot be fixed by waiting — retrying is noise; swallowing it hides it from the operator.
**Fix:** `TradingViewUserNotFoundException extends DataValidationException` → ignore-listed from retries and mapped to HTTP 400 for admin/user callers. The async payment path can't 400 anyone: it marks the sub GRANTED (customer paid) and enqueues a DEAD retry row so the admin TV-retry page shows "action required" (`listener/SubscriptionChangeStatusListener.java:65-74`). Commit `8db6481`.

## Subscriptions & Stripe lifecycle

### Backend cron canceled ACTIVE Stripe subscriptions

**Symptom:** users with a healthy Stripe subscription got their subscription deleted in Stripe by our own hourly job.
**Root cause:** the expiry scheduler trusted local `expired_at` and issued Stripe `DELETE /v1/subscriptions/...` before Stripe's real renewal boundary (local expiry can lag: webhook latency, drift).
**Fix:** Stripe is the source of truth for Stripe-backed timing. Local expiry/grace reconciliation NEVER calls Stripe cancel — it only logs (`SubscriptionDeactivationService.logSkippedStripeCancellation`, lines 70–77). Stripe cancel happens only on *intentional* conversion away from Stripe billing (switch to non-Stripe payment `SubscriptionService.java:363-365`, lifetime grant `SubscriptionService.java:405-407`). Commit `b7a1a16`.

### Monthly subscribers got ~2 months per payment (two independent bugs)

**Symptom:** monthly renewals drift longer and longer; occasionally a single payment adds two months at once.
**Root cause (a):** renewal expiry was computed as `old_expiredAt + 2 grace days + 30` — the +2 "payment grace" compounded every cycle (+1 month after ~15 renewals). **(b):** CryptoCloud retries postbacks; two concurrent webhooks both read the order as PENDING and both extended the sub.
**Fix (a):** Stripe renewals mirror `CURRENT_PERIOD_END` exactly; non-Stripe renewals extend by exact plan duration, no hidden grace (`SubscriptionService.extendExistingSubscription`, lines 353–361). **(b):** `OrderService.processSuccessfulPayment` fetches the order via `findByIdForUpdate` — `@Lock(PESSIMISTIC_WRITE)` (`repository/OrderRepository.java:17-19`, `OrderService.java:117`); the loser of the race sees the PAID order and exits via the idempotency guard.

### Expired trial pulled a new paid subscription backward

**Symptom:** a current payment creates an already-expired paid subscription and
the next hourly run moves it straight to grace (order #997, 2026-08-19).
**Root cause:** trial conversion unconditionally used the old trial expiry as
the duration base; active renewal did the same for any stale paid row.
**Fix:** non-Stripe purchases use `max(now UTC, existing expiredAt)` before
adding plan duration. Payment handling locks both the order and user so two
different callbacks cannot extend from the same stale subscription state.

### Stripe lifecycle webhooks updated the DB but not TradingView

**Symptom:** `customer.subscription.updated` moves `current_period_end` (plan change, pause, dunning), the DB row is correct, but the user's bot access still carries the old expiry.
**Root cause:** TV access was only refreshed by payment/order events; lifecycle syncs bypassed them.
**Fix:** `syncStripeSubscriptionUpdated` publishes the normal `EXTENDED` event when (and only when) `expired_at` actually changed (`SubscriptionService.java:229-231`); metadata-only updates (status, `cancel_at_period_end`) stay quiet so we don't spam the bot. `customer.subscription.deleted` terminates locally without calling Stripe cancel back; both handlers ignore rows already converted away from Stripe (`SubscriptionService.java:220-224`). Commit `96a1866`.

### Grace/reconciliation scope is deliberately narrow

**Symptom:** an "obviously stale" subscription isn't touched by past-grace reconciliation.
**Root cause / fix:** reconciliation only terminates non-trial MONTH/YEAR subs ≥7 days past expiry (`SubscriptionStateReconciliationService.java:23,115-128`; `GRACE_PERIOD_DAYS = 7` at `SubscriptionService.java:74`). LIFETIME is excluded by design (commit `c5ff406`) — don't "fix" that.

## Payment webhooks

### Stripe webhook delivery can stop silently — money collected, nobody activated

**Symptom:** Stripe dashboard shows Succeeded payments; backend shows PENDING orders; zero errors in our logs — the endpoint simply receives nothing (Apr 2026: ~31h gap, see `claude-payment-issue/01-INVESTIGATION-SUMMARY.md`).
**Root cause:** delivery is invisible from the backend. Stripe auto-disables endpoints after repeated failures, and we made failures easy: unsupported event types returned HTTP 502 (`PaymentProcessingException`) instead of a 2xx ACK.
**Fix:** unhandled event types are now signature-verified, logged, and ignored with success (`StripePaymentService.processWebhook`, lines 239–245). Resending old events is safe: duplicate `invoiceId` is skipped (`OrderService.java:140,185-189`) and orders are row-locked. Detection is still external: watch PENDING Stripe orders in DB / Loki `StripePaymentService` silence, and check the Stripe dashboard's endpoint status first, not the code. Reconciliation SQL lives in `claude-payment-issue/`.

### Webhook signatures verify the RAW body

**Symptom:** signature failures with a correct secret after a refactor.
**Root cause / fix:** `Webhook.constructEvent` gets the raw `payload` string (`StripePaymentService.java:286-296`) — never re-serialize a parsed body before verification. `SignatureVerificationException` → `SecurityException` → HTTP 400 (a burst of these will get the endpoint auto-disabled by Stripe; a quiet secret rotation looks exactly like this).

### CryptoCloud sends postbacks as form-urlencoded OR JSON, with snake_case fields

**Symptom:** some CryptoCloud postbacks 415/fail to bind; invoice options are silently ignored by their API.
**Root cause:** CryptoCloud V2 uses either content type per postback; its request/response fields are snake_case, so un-annotated camelCase Lombok fields serialize wrong and are silently dropped (`time_to_pay` was ignored for months).
**Fix:** two `@PostMapping("/crypto")` handlers, one per `consumes` (`PaymentController.java:43-50`); explicit `@JsonProperty` on every snake_case field and required `currency` (`dto/payment/crypto/CryptoInvoiceCreateDto.java`). Filter postbacks by `status == "success"`.

## Migrations

### Flyway versions are allocated serially — parallel branches collide

**Symptom:** two branches both add `V15__*.sql`; the second one to merge fails validation (or, if versions interleave under a higher applied version, is refused as out-of-order).
**Fix:** check `src/main/resources/db/migration/` (currently V1–V14) and claim the next number at branch time; coordinate when two workstreams both need one. Never renumber or edit an applied migration — write a new one (V14 patching V-old lifetime rows is the model).
