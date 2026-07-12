# Critical Contracts

System-wide invariants. Code that violates one of these is a defect
even if it "works". Each contract cites the code that enforces it;
the subsystem deep-dive index is `../architecture/README.md`.

<!-- Numbered (C1, C2, …) — these numbers get referenced in commit
messages, code comments, and audits. Once a contract has a number,
NEVER renumber. New contracts get the next free number; retired ones
stay in this list as struck-through with their retirement DEC. -->

- **C1.** The TV access bot always receives an expiry padded by
  `BOT_EXPIRY_BUFFER_DAYS` (1 day) — every bot payload is built via
  `ActivateTradingViewAccessDto.grant()` / `bufferBotExpiration()`,
  never with a raw `expiredAt` (pad skipped for lifetime; padded value
  is what gets persisted to the retry queue, so replays don't compound).
  `src/main/java/com/winworld/coursestools/dto/external/ActivateTradingViewAccessDto.java:31-68`.
- **C2.** Stripe-backed subscription expiry IS Stripe's
  `currentPeriodEnd` — set from webhook data only, never edited
  manually. Every manual/admin expiry path must call
  `ensureNotStripeManaged()` and reject Stripe subs.
  `src/main/java/com/winworld/coursestools/service/SubscriptionService.java:630-636`
  (guard), `:279-285` (lifecycle sync), `:602-608` (payment).
- **C3.** The backend never cancels a subscription at Stripe during
  expiry reconciliation — crons only log a skip. Stripe-side
  cancellation happens in exactly two flows: the user pays with a
  non-Stripe method (payment-method switch) or receives a lifetime
  grant; Stripe-initiated termination arrives only via the
  `customer.subscription.deleted` webhook.
  `src/main/java/com/winworld/coursestools/service/SubscriptionDeactivationService.java:70-77`,
  `SubscriptionService.java:363-365`, `:405-407`, `:236-268`.
- **C4.** Lifetime = sentinel `expiredAt` **2100-12-31 23:59:59** plus
  `isLifetime: true` on every bot payload (TradingView rejects year
  9999 — see migration `V14__fix_lifetime_expiry_year.sql`).
  `src/main/java/com/winworld/coursestools/service/SubscriptionService.java:75`.
- **C5.** A successful payment flips the subscription only by
  publishing `SubscriptionChangeStatusEvent`
  (CREATED / RESTORED / EXTENDED); the async
  `@TransactionalEventListener` then grants TV access and sets status
  GRANTED after the payment transaction commits. Payment paths never
  set GRANTED synchronously.
  `src/main/java/com/winworld/coursestools/service/SubscriptionService.java:188-207`,
  `src/main/java/com/winworld/coursestools/listener/SubscriptionChangeStatusListener.java:50-78`.
- **C6.** Any `expiredAt` change that reaches the DB must also reach
  the TV bot — publish EXTENDED (or call activation directly). Stripe
  lifecycle sync publishes EXTENDED only when the expiry actually
  changed.
  `src/main/java/com/winworld/coursestools/service/SubscriptionService.java:229-231`, `:587`.
- **C7.** Payment webhook processing is idempotent: the order row is
  locked `findByIdForUpdate`, an already-paid non-recurrent order is
  rejected, and recurrent Stripe replays are deduped by comparing the
  incoming `invoiceId` against the one stored on the subscription.
  `src/main/java/com/winworld/coursestools/service/OrderService.java:117-145`, `:181-191`.
- **C8.** Trial is one-per-TradingView-nickname (case-insensitive,
  recorded in `trial_activations`) AND one-per-user-per-subscription
  -type; 7 days (`subscription.ct-pro.trial.days`), always the PRO
  monthly plan.
  `src/main/java/com/winworld/coursestools/service/SubscriptionService.java:126-136`,
  `src/main/java/com/winworld/coursestools/repository/TrialActivationRepository.java:11-16`,
  `src/main/resources/application.yml:103`.
- **C9.** Partner codes are PRO-only: created with `tier = PRO`, and a
  tiered code applies only to plans of the same tier (enforced at both
  order creation and code validation; backfilled by
  `V9__partner_codes_pro_only.sql`).
  `src/main/java/com/winworld/coursestools/service/CodeService.java:41-50`, `:95-97`,
  `src/main/java/com/winworld/coursestools/service/OrderService.java:75-77`.
- **C10.** Signup requires a real TradingView nickname, verified live
  with `GET https://tradingview.com/u/{name}` — a 404 aborts
  registration.
  `src/main/java/com/winworld/coursestools/service/AuthService.java:108`,
  `src/main/java/com/winworld/coursestools/service/external/TradingViewService.java:20-38`.
- **C11.** TV bot failures never roll back subscription state:
  transient failures go through resilience4j `@Retry` and then a
  durable retry queue (`trading_view_retry_jobs`, drained by a
  scheduler); the permanent nickname-not-found error becomes an
  immediate DEAD job surfaced on the admin TV-retry page — the paying
  user stays GRANTED in the DB either way.
  `src/main/java/com/winworld/coursestools/service/external/ActivatingSubscriptionService.java:29-107`,
  `src/main/java/com/winworld/coursestools/listener/SubscriptionChangeStatusListener.java:63-77`.
- **C12.** Subscription expiry math is UTC: "now" is
  `LocalDateTime.now(ZoneOffset.UTC)` and Stripe epoch seconds convert
  with `ZoneOffset.UTC` — never the JVM default zone (the prod
  container runs UTC; the VPS host does not).
  `src/main/java/com/winworld/coursestools/service/SubscriptionService.java:593-595`, `:281-284`.
- **C13.** Grace period is 7 days (`GRACE_PERIOD_DAYS`) after expiry;
  paid statuses only flow PENDING → GRANTED → GRACE_PERIOD →
  TERMINATED, and past-grace reconciliation is fail-safe (startup
  runner + hourly cron + read-time discard + health gauge).
  `src/main/java/com/winworld/coursestools/service/SubscriptionService.java:74`,
  `src/main/java/com/winworld/coursestools/service/SubscriptionStateReconciliationService.java:126-128`,
  `src/main/java/com/winworld/coursestools/service/user/UserSubscriptionService.java:37`.
- **C14.** Flyway migrations are append-only: never edit an applied
  `V*.sql` (Flyway checksum validation fails the app at startup); a
  schema change is always a new file with the next V number.
  `src/main/resources/db/migration/` (V1–V14),
  `build.gradle.kts:69`.
- **C15.** Any push to `master` auto-deploys production: GitHub
  Actions builds the image and runs
  `docker compose pull backend && docker compose up -d backend` on the
  prod VPS. Treat every master push as a prod release.
  `.github/workflows/docker-build.yml:3-5`, `:70-71`.

Changing a contract requires: a decision record in
`../../decisions/`, the architecture doc update, and this file's
update — in the same commit as the code change.
