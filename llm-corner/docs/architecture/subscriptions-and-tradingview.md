# Subscriptions & TradingView Access

The core subsystem: a `UserSubscription` row is the paid-access source of
truth, and every grant/extension is mirrored to the external TradingView
access bot ("TV bot") that actually toggles indicator access on
tradingview.com. All paths below are relative to
`src/main/java/com/winworld/coursestools/`.

## Data model

- Tiers: `ESSENTIALS`, `PRO` (`enums/SubscriptionTier.java`). Plans:
  `MONTH`, `YEAR`, `LIFETIME`, `TRIAL` (`enums/Plan.java`).
- `SubscriptionPlan` (table `subscription_plans`) = (plan name, tier,
  `durationDays`, price, `discountMultiplier`) under a `SubscriptionType`
  (only type: `COURSESTOOLS`, i.e. CT-Pro).
- `UserSubscription` (table `users_subscriptions`,
  `entity/user/UserSubscription.java`): `status`, `isTrial`, `expiredAt`
  (UTC `LocalDateTime`), `paymentMethod`, `paymentProviderData` (jsonb —
  Stripe period end / status / cancel flag), `plan`, `user`.
- Statuses (`enums/SubscriptionStatus.java`): `PENDING` (paid/created,
  TV activation not confirmed yet) → `GRANTED` (listener confirmed) →
  `GRACE_PERIOD` (expired, 7-day grace) → `TERMINATED`.
- Constants: `GRACE_PERIOD_DAYS = 7` (`service/SubscriptionService.java:74`);
  lifetime sentinel `LIFETIME_EXPIRY = 2100-12-31T23:59:59`
  (`SubscriptionService.java:75`) — was year 9999, but the TV bot rejects
  dates that far out; migration `V14__fix_lifetime_expiry_year.sql`
  rewrote rows and queued retry payloads.
- Trial length: `subscription.ct-pro.trial.days: 7` (`application.yml:103`).

## Lifecycle flows (all in `SubscriptionService`)

- **Trial** — `POST /api/v1/subscriptions/start-trial` →
  `activateCtProTrialForUser` (`:121`): forced PRO MONTH plan, price 0,
  `isTrial=true`, `expiredAt = now(UTC) + 7d`, status `PENDING`. One trial
  per TradingView nickname ever, enforced via `trial_activations`
  (`existsByTradingviewUsername`) plus "ever had a sub" check.
- **Payment success** — `OrderService.java:147` calls
  `updateUserSubscriptionAfterPayment` (`:189`), which branches:
  - no sub or trial → `createNewSubscription` (`:296`); trial gets
    `TERMINATED`; active trial time is credited, while an expired trial uses
    now as the base; publishes `CREATED`.
  - `GRACE_PERIOD` → `updateGracePeriodSubscription` (`:327`), expiry from
    now; publishes `RESTORED`.
  - else → `extendExistingSubscription` (`:347`); publishes `EXTENDED`.
  - Expiry rule: if `paymentProviderData` has Stripe
    `CURRENT_PERIOD_END`, mirror that epoch exactly (Stripe owns the
    billing boundary); otherwise use
    `max(now UTC, existing expiredAt) + plan.durationDays`. Payment handling
    pessimistically locks the user before re-reading the current subscription,
    serializing different paid orders for the same user.
  - Switching a Stripe-backed sub to another method cancels the Stripe
    subscription first (`:335`, `:363`).
- **Stripe lifecycle sync** — webhook-driven
  `syncStripeSubscriptionUpdated` (`:210`) mirrors period end into
  `expiredAt` (publishes `EXTENDED` when it changed);
  `handleStripeSubscriptionDeleted` (`:236`) terminates and publishes
  `GRACE_PERIOD_END`. Both no-op if the local sub is no longer
  Stripe-backed.
- **Expiry → grace** — hourly scheduler: `GRANTED` + expired →
  `SubscriptionDeactivationService.deactivateSingleSubscription`
  (REQUIRES_NEW per row): status `GRACE_PERIOD`, referral deactivated,
  `GRACE_PERIOD_START` published. Stripe-backed subs are only logged, never
  cancelled at Stripe from the cron (`SubscriptionDeactivationService.java:70-77`).
- **Grace → terminated** — `SubscriptionStateReconciliationService
  .reconcilePastGracePeriodSubscriptions` terminates non-trial MONTH/YEAR
  subs with `expiredAt <= now(UTC) - 7d` and publishes `GRACE_PERIOD_END`.
  Runs hourly, at startup (`SubscriptionPastGracePeriodStartupReconciler`),
  and lazily via `discardPastGracePeriodSubscription`; exports gauge
  `subscriptions.past_grace_period.count`.
- **Trial expiry** — hourly: straight to `TERMINATED`, `TRIAL_ENDED`.
- **Admin grants** (`AdminService` → `SubscriptionService:436-589`):
  `adminGrantPaid` (MONTH/YEAR via a transient never-persisted Order through
  the canonical payment path, or `keepExpirationDate=true` tier swap),
  `adminGrantLifetime` (`:496`), `adminGrantTrial` (`:514`),
  `adminCustomUpdateExpiry` (`:576`, pure expiry bump). Manual expiry edits
  on Stripe-backed subs are rejected (`ensureNotStripeManaged`, `:630`).
  `POST /api/v1/subscriptions/activate` (ADMIN) sets expiry by TV username.
- Lifetime/admin-activate paths call the TV bot **synchronously in the
  transaction** and set `GRANTED` directly (`:390-395`, `:411-415`,
  `:622-627`); everything else goes through the async listener below.

## Async activation: event → listener → TV bot

`SubscriptionMapper.toEvent` builds `SubscriptionChangeStatusEvent` with an
immutable bot snapshot (user/subscription IDs, email, TradingView username,
tier, expiration, lifetime, event type, and `tradingViewExpirationPolicy`).
Every publisher must explicitly select `CUSTOMER_PAYMENT_BUFFER` or `EXACT`
(DEC-002). Before publication, activation-producing transactions persist the
final DTO plus a fresh `activationCommandId` into the user's single PENDING
ACTIVATE retry slot.
`listener/SubscriptionChangeStatusListener.activateUserSubscription` is
`@TransactionalEventListener` (after commit) + `@Async` + REQUIRES_NEW
(`:50-52`) and reacts only to `CREATED`, `TRIAL_CREATED`, `EXTENDED`,
`RESTORED` (`:31-36`). It locks the subscription, then the staged retry row,
and delivers only if the command ID and snapshot are still current. A newer
admin/payment/Direct command therefore supersedes an older async event instead
of inheriting its policy. A superseded event may still reconcile PENDING to
GRANTED only when its committed snapshot still matches; it never revives
GRACE_PERIOD/TERMINATED state.
Snapshot expiration comparison honors PostgreSQL's microsecond timestamp
precision. A matching command token with a genuine snapshot mismatch is an
invariant failure: the command is moved to DEAD with field-level diagnostics
instead of being deleted.
`GRACE_PERIOD_START/END` and `TRIAL_ENDED` trigger **no bot call — there is
no revoke channel**; access dies when the expiration sent earlier passes.
The email-notification listener body is currently commented out (`:82-84`).

## TV bot contract (`dto/external/`)

`POST ${urls.activating-bot}` (env `ACTIVATING_BOT_URL` =
`http://45.141.184.24:4320/open`) with `ActivateTradingViewAccessDto`:
`email`, `tier`, `tv` (`@JsonProperty` for `tradingViewName`),
`expiration` (naive ISO-8601 `LocalDateTime`, serializer pinned to
`ISO_LOCAL_DATE_TIME` in `config/ApplicationConfiguration.java:38-49`),
`isLifetime`. Rename: `POST ${urls.change-tradingview-bot}`
(`.../username_changer`) with `ChangeTradingViewNameDto`: `old`, `new`,
`tier`, `expiration`, `isLifetime` — sent from
`UserSocialService.bindUserTradingView` (`:67-104`), which captures the old
name *before* mutating and skips the bot on case-only changes.

**Customer-payment +1 day pad**:
`CUSTOMER_PAYMENT_EXPIRY_BUFFER_DAYS = 1`. A successful customer payment
(first purchase, renewal, or grace restoration) uses
`customerPaymentGrant()`; all trials, admin grants/updates, Stripe lifecycle
syncs, Direct Extend, and renames use exact factories. The pad protects paid
users from the offset-less bot timestamp and late renewal delivery without
silently extending manual access. The final value is persisted to the retry
queue, so replay never compounds it. Lifetime is never padded (DEC-002).

## Failure handling & durable retry queue

`service/external/ActivatingSubscriptionService` wraps both bot calls in
resilience4j `@Retry(name = "default")`: 3 attempts, exponential 1s→5s;
`HttpClientErrorException` and `DataValidationException` are ignore-listed
(`application.yml:59-70`). Behavior:

- Bot 404 → `TradingViewUserNotFoundException` (extends
  `DataValidationException`, so never retried) — the TV nickname doesn't
  exist (`:33-37`). Dedicated fallback overloads rethrow it (`:97-107`);
  the async listener then still marks the sub `GRANTED` (customer paid) and
  converts the same staged PENDING command to DEAD so the admin retry page
  shows "nickname invalid — action required". Admin
  grant / self-bind paths surface it as a 400 instead.
- Other non-2xx / IO failure → after in-process retries the `Throwable`
  fallback **does not throw**: it enqueues a durable job so the caller's
  transaction still commits (`:72-87`).

Queue: table `trading_view_retry_jobs`
(`db/migration/V11__trading_view_retry_jobs.sql`, +`force_retry_count` in
V12, +`command_id` in V15): jsonb `payload`, `attempts`, `next_attempt_at`, `last_error` (2048,
includes bot status+body), statuses `PENDING`/`DEAD`, types
`ACTIVATE`/`RENAME`. Partial unique index `(user_id, type) WHERE
status='PENDING'` — a fresh enqueue overwrites the stale PENDING job.
For ACTIVATE, `command_id` turns that row into the latest-command outbox:
producer staging, async listeners, Direct Extend, synchronous grants, and the
retry scheduler all serialize on the same locked row. Success deletes it;
transient failure keeps the final payload; a stale command ID is never sent.
`service/external/TradingViewRetryService`:

- `enqueue` (`:56`) — outbox pattern, caller's tx; `enqueueDead` (`:102`) —
  immediate DEAD for permanent errors, deduped per (user, type, DEAD).
- `processDueJobs` (`:177`) — every minute, REQUIRES_NEW, `FOR UPDATE SKIP
  LOCKED` batch of 20; success deletes the row; failure backs off per
  `tradingview.retry.backoff-seconds` = 60,300,900,3600,21600,86400×5
  (`application.yml:105-109`); after `max-attempts: 10` → `DEAD`.
- `forceRetry` (`:228`) — admin "Retry now": single atomic UPDATE (resets
  attempts, `force_retry_count++`, schedules now); a DEAD row superseded by
  a fresher PENDING is dropped and the click redirected. `drop` (`:260`) —
  idempotent delete.
- `onUserTradingViewNameChanged` (`:147`) — patches PENDING **and** DEAD
  ACTIVATE payloads when a user renames, so retries target the new handle.

### Orphaned PENDING reconciliation

At application startup and every five minutes,
`PendingSubscriptionReconciliationService` scans subscriptions that have
remained PENDING for at least 15 minutes. Each candidate is revalidated under
the user -> subscription -> ACTIVATE lock order. Existing PENDING jobs remain
owned by the retry scheduler and DEAD jobs remain visible to admins. A current
candidate with no command is re-staged: trials receive a fresh seven-day exact
grant, paid subscriptions preserve DB expiry and restore the customer-payment
buffer, and MANUAL grants remain exact. Superseded rows are never revived.
Gauge: `subscriptions.stuck_pending.count`.

Admin API (`controller/AdminController.java:115-139`, ADMIN role):
`GET /api/v1/admin/tv-retry/jobs` (filter + pageable, sort whitelist, page
≤ 100), `GET .../jobs/{id}`, `POST .../jobs/{id}/retry`,
`DELETE .../jobs/{id}`.

## Schedulers (`scheduler/`, crons in `configs/scheduler.yml`)

| Job | Cron | Does |
|---|---|---|
| `SubscriptionScheduler.deactivateExpiredSubscriptions` | `0 0 * * * *` (hourly) | GRANTED+expired → GRACE_PERIOD; unsubscribes user's alerts |
| `SubscriptionScheduler.cleanupExpiredTrialSubscriptions` | `0 0 * * * *` | every non-terminated expired trial (PENDING/GRANTED/GRACE_PERIOD) → TERMINATED; unsubscribes alerts |
| `SubscriptionScheduler.cleanupExpiredGracePeriodSubscriptions` | `0 0 * * * *` | past-grace reconciliation → TERMINATED |
| `TradingViewRetryScheduler.pollDueJobs` | `0 * * * * *` (every minute) | drains due PENDING retry jobs |
| `SubscriptionScheduler.reconcileStuckPendingSubscriptions` | `0 */5 * * * *` | re-stages orphaned PENDING subscriptions older than 15 minutes |

`scheduler/OrderScheduler.java` is an empty TODO stub (unpaid-order cleanup
was never built).

## Gotchas

- `PENDING` is a *normal short-lived* post-payment state; it means "TV
  activation not yet confirmed", not "unpaid". A durable command is retried;
  an orphan older than 15 minutes is rebuilt by the reconciler.
- All expiry math is UTC (`LocalDateTime.now(ZoneOffset.UTC)`); the +1 day
  payment-only bot pad exists precisely because the bot side is *not*
  UTC-aware.
- Paid expiry selection explicitly excludes trials. Each paid candidate is
  pessimistically locked and revalidated before moving to grace, so scheduler
  ordering and a concurrent renewal cannot overwrite fresh state.
- `POST /api/v1/admin/access/direct` is TV-only: it requires a
  non-terminated subscription to inherit email/tier/lifetime, sends the exact
  selected date, may write retry bookkeeping, and never mutates business
  subscription/order/transaction state.
- Backend cron never cancels Stripe subscriptions; Stripe-backed local
  expiry is driven by Stripe webhooks only, and manual expiry edits on
  Stripe subs are rejected with a 400.
- Don't add a strict `@JsonFormat` to the DTO `expiration` fields: it would
  also constrain deserialization and poison legacy fractional-second
  payloads already stored in `trading_view_retry_jobs.payload`
  (`ActivateTradingViewAccessDto.java:37-43`).
