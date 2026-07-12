# Persistence, Scheduling & Events

How CT backend state is stored (JPA entities + Flyway), which background jobs
run when, and which in-process application events glue domains together.
All paths relative to `src/main/java/com/winworld/coursestools/` unless noted.

## Entity map

```
                              User (users)
                               | 1:1 (@MapsId, PK = user_id)
        +----------+-----------+-----------+----------------+
   UserProfile  UserSocial  UserFinance  UserPartnership   Code (partnerCode, owner_id)
   country_code TV name,    balance      level_rank,        |
                telegram,                custom_cashback1/2 | N:1
                discord                                     v
   User 1:N ->  UserSubscription (users_subscriptions) --N:1--> SubscriptionPlan --N:1--> SubscriptionType
   User 1:N ->  Order (orders) --N:1--> SubscriptionPlan, --N:1--> Code
   User 1:N ->  UserTransaction (users_transactions) --N:1--> Order
   User 1:N ->  UserAlert (users_alerts, composite PK user_id+alert_id) --N:1--> Alert
   User 1:N ->  Referral (referrals: referrer_id N:1, referred_id 1:1 unique)
   User 1:N ->  TradingViewRetryJob (trading_view_retry_jobs)
```

- All entities extend `BaseEntity` (identity `Integer id`) or `Auditable`
  (`created_at`/`updated_at` via `AuditingEntityListener`; enabled by
  `@EnableJpaAuditing` in `CoursesToolsApplication.java:12`).
- `User` (`entity/user/User.java:37`) cascades ALL to its four 1:1 satellites
  and its partner `Code`.
- `UserSubscription` (`entity/user/UserSubscription.java:38`): `status`
  (PENDING/GRANTED/GRACE_PERIOD/TERMINATED, `enums/SubscriptionStatus.java`),
  `isTrial`, `expiredAt`, `paymentMethod` (CRYPTO/STRIPE/PAYEER/BALANCE/MANUAL),
  and `payment_provider_data` jsonb (holds Stripe subscription metadata).
- `SubscriptionPlan` (`entity/subscription/SubscriptionPlan.java`): `name`
  (MONTH/YEAR/LIFETIME/TRIAL), `tier` (PRO/ESSENTIALS), `durationDays`, `price`,
  `discountMultiplier`; N:1 to `SubscriptionType` (COURSESTOOLS, MENTORSHIP).
- `Order` (`entity/Order.java`): status PENDING/PAID, orderType
  ONE_TIME/RECURRENT, optional promo `Code`, original/total price.
- `UserTransaction`: amount + type (WITHDRAWAL/PURCHASE), optional link to Order.
- `Code` (`entity/Code.java`): promo/partner code; `owner != null` means
  partner code (`isPartnershipCode()`); optional `subscriptionType` + `tier` scoping.
- `TradingViewRetryJob` (`entity/TradingViewRetryJob.java:32`): outbox-style
  retry row — type ACTIVATE/RENAME, status PENDING/DEAD, jsonb payload,
  `nextAttemptAt`, `attempts`, `lastError`, `forceRetryCount`.
- `TierIndicatorPermission`: allowlist row (tier, indicator, subscription_type);
  no rows for a tier = unrestricted (PRO model).
- `Alert`: catalog of alert definitions (type/broker/tf/event/asset/indicator);
  `UserAlert` is the user's subscription to one, with jsonb `properties`.

### Tables with no JPA entity (native SQL only)

- `trial_activations` — one trial per lowercased TradingView username, ever.
  No `TrialActivation` entity exists; `repository/TrialActivationRepository.java:10-16`
  hosts native EXISTS/INSERT queries (declared over `UserSocial` as a carrier type).
- `codes_usages` — written/counted natively in `repository/CodeRepository.java:15-36`.
- `referrals_earnings` — per-level cashback rows, written/aggregated natively in
  `repository/ReferralRepository.java:21-34`.

## Flyway migrations (src/main/resources/db/migration/)

| File | Purpose |
|---|---|
| `V1__initial.sql` | Full initial schema: users + 4 satellite tables, subscription types/plans, users_subscriptions, referrals + referrals_earnings, codes + codes_usages, orders, users_transactions, alerts, users_alerts, news. |
| `V2__insert_plans.sql` | Seed types (COURSESTOOLSPRO, MENTORSHIP) and 4 plans (Pro Month/Year/Lifetime, Mentorship Month). |
| `V3__user_alerts_pk.sql` | Drop surrogate id on users_alerts; composite PK (user_id, alert_id). |
| `V4__create_trial_activations.sql` | trial_activations table (unique TV username) + backfill from existing Pro subscriptions. |
| `V5__migrate_payeer_to_crypto.sql` | Rewrite payment_method PAYEER -> CRYPTO in users_subscriptions. |
| `V6__add_tiers_and_essentials.sql` | `tier` column on plans (default PRO), rename type to COURSESTOOLS, seed 3 ESSENTIALS plans, create tier_indicator_permissions (ESSENTIALS -> wcsmc only), add code scoping columns. |
| `V7__add_smctb_alerts_and_tier_defaults.sql` | Inserted smctb mirror alerts for 9 SMC ToolBox events (approach was wrong — superseded by V8) + tier/code guards. |
| `V8__fix_smctb_alerts.sql` | Fix V7: clear users_alerts, delete duplicated smctb rows, UPDATE the 9 wcsmc events to smctb in place. |
| `V9__partner_codes_pro_only.sql` | Owner-bound (partner) codes restricted to tier = PRO. |
| `V10__add_past_grace_subscription_index.sql` | Partial index on expired_at where is_trial = false and status <> TERMINATED (feeds past-grace reconciliation). |
| `V11__trading_view_retry_jobs.sql` | Retry-job table + (status, next_attempt_at) index + unique partial index: one PENDING job per (user, type). |
| `V12__tv_retry_force_retry_count.sql` | Add force_retry_count to retry jobs. |
| `V13__custom_cashback_columns.sql` | custom_cashback1/2 overrides on user_partnership. |
| `V14__fix_lifetime_expiry_year.sql` | Lifetime sentinel 9999-12-31 -> 2100-12-31 (TV rejects 9999) + patch retry-job payloads, set isLifetime. |

## Schedulers

Enabled by `@EnableScheduling` (`CoursesToolsApplication.java:14`). Cron values
come from `src/main/resources/configs/scheduler.yml` (imported via
`application.yml:34`). Spring 6-field cron: `0 0 * * * *` = hourly on the hour,
`0 * * * * *` = every minute.

| Scheduler | Method | Cron (property) | What it does |
|---|---|---|---|
| `scheduler/SubscriptionScheduler.java:19` | `deactivateExpiredSubscriptions` | hourly (`scheduler.subscription.expired-subscriptions`) | GRANTED + expired paid subs -> GRACE_PERIOD (each in REQUIRES_NEW tx, `service/SubscriptionDeactivationService.java`), deactivate referral, publish GRACE_PERIOD_START, then unsubscribe user from all alerts. |
| `scheduler/SubscriptionScheduler.java:27` | `cleanupExpiredTrialSubscriptions` | hourly (`...trial-expired-subscriptions`) | Expired trials -> TERMINATED, publish TRIAL_ENDED, unsubscribe alerts. |
| `scheduler/SubscriptionScheduler.java:35` | `cleanupExpiredGracePeriodSubscriptions` | hourly (`...grace-period-expired-subscriptions`) | `SubscriptionStateReconciliationService.reconcilePastGracePeriodSubscriptions("scheduler")` — MONTH/YEAR subs past expiry + 7 days (`SubscriptionService.java:74` GRACE_PERIOD_DAYS) -> TERMINATED, publish GRACE_PERIOD_END. |
| `scheduler/TradingViewRetryScheduler.java:16` | `pollDueJobs` | every minute (`scheduler.tv-retry.poll`) | `TradingViewRetryService.processDueJobs`: claims due PENDING jobs with `FOR UPDATE SKIP LOCKED` (batch 20, `repository/TradingViewRetryJobRepository.java:22`), POSTs to TV bot; success deletes the row, failure backs off 60s->24h, DEAD after 10 attempts (defaults in `service/external/TradingViewRetryService.java:41-48`). |
| `scheduler/OrderScheduler.java` | — | — | Empty shell; only a TODO to clean up unpaid orders. No @Scheduled method. |

Also startup-time: `service/SubscriptionPastGracePeriodStartupReconciler.java`
runs the same past-grace reconciliation on `ApplicationReadyEvent`.

## Application events

Async infra: `@EnableAsync` (`CoursesToolsApplication.java:13`) + executor in
`config/AsyncConfig.java`. Listeners extend `AbstractNotificationListener`
(message-builder fan-out to `EmailService`).

| Event | Published by | Listener(s) | Effect |
|---|---|---|---|
| `UserCreateEvent` (id, forwardedFor, email, generatedPassword) | `service/AuthService.java:73` (signup), `:94` (Google signup) | `listener/UserCreateListener.java:40` `setUserRegion` (@Async @EventListener, REQUIRES_NEW): GeoLocation lookup -> `user_profile.country_code`; `:48` `sendNotificationEmail` (@Async @EventListener): welcome emails via message builders. | Region + welcome email after registration. |
| `SubscriptionChangeStatusEvent` (email, tradingViewUsername, userSubscriptionId, eventType) | `service/SubscriptionService.java` (trial create :150, trial end :179, after-payment CREATED/RESTORED/EXTENDED :197-:205, Stripe lifecycle sync :230/:265, admin grants :491/:538/:566/:587), `service/SubscriptionDeactivationService.java:41` (GRACE_PERIOD_START), `:67` (GRACE_PERIOD_END) | `listener/SubscriptionChangeStatusListener.java:53` `activateUserSubscription` (@TransactionalEventListener @Async, REQUIRES_NEW): only for CREATED/TRIAL_CREATED/EXTENDED/RESTORED — calls TV access bot; on permanent nickname-not-found, enqueues DEAD retry row, still marks sub GRANTED. `:82` email handler exists but body is commented out. | TV access grant is driven entirely by this event, post-commit. |
| `UserAlertsChangeEvent` (telegramId) | `service/AlertService.java:146,164,172` (alert subscribe/unsubscribe paths) | `listener/UserAlertChangeListener.java:30` (@TransactionalEventListener @Async): POST to alert bot `${urls.alert-bot}` with telegramId. | Alert bot re-syncs the user's alert set. |

`SubscriptionEventType` values: CREATED, RESTORED, EXTENDED, TRIAL_CREATED,
TRIAL_ENDED, GRACE_PERIOD_START, GRACE_PERIOD_END (`enums/SubscriptionEventType.java`).
Trial length: `subscription.ct-pro.trial.days: 7` (`application.yml:103`).

## Gotchas

- `UserCreateListener` uses plain `@EventListener` (fires at publish time,
  inside the still-open signup transaction) while the other two listeners use
  `@TransactionalEventListener` (after commit). `setUserRegion` reads the user
  in a REQUIRES_NEW tx and can race the committing signup tx.
- `SubscriptionChangeStatusListener.sendNotificationEmail` is a no-op — the
  `sendEmails` call is commented out (`listener/SubscriptionChangeStatusListener.java:83`).
- `SubscriptionPlan` maps the same `mappedBy = "plan"` `List<UserSubscription>`
  twice (`subscriptions` and a misnamed `orders` field) — copy-paste artifact,
  harmless but confusing (`entity/subscription/SubscriptionPlan.java`).
- Retry-job enqueue is transactional with its caller (outbox pattern): rollback
  of the producing tx also rolls back the retry row
  (`service/external/TradingViewRetryService.java:50-56`).
- Lifetime subs use expiry sentinel 2100-12-31 (not 9999) since V14; TV bot
  additionally receives an `isLifetime` flag.
- One trial per TradingView username for life, enforced case-insensitively via
  `trial_activations` (V4) — deleting the user cascades the row away.
- DB timestamps are mostly `timestamptz` (V1) but entities use `LocalDateTime`;
  the prod container runs UTC — compare times accordingly. The retry-job table
  (V11) uses plain `TIMESTAMP`.
