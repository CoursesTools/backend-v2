# Architecture

System overview + index into per-subsystem deep dives.

CoursesTools (CT) backend: Java 17 / Spring Boot 3.2.4 monolith serving the
CT-Pro subscription SaaS. Paying users get access to premium TradingView
indicators, granted by an external "TV access bot". PostgreSQL is the system
of record (Flyway-migrated); Redis holds short-lived one-time tokens
(password-reset / email-verification / Telegram-bind, `service/TokenService.java`)
— auth JWTs are stateless, nothing session-like is stored. Single Gradle module,
root package `com.winworld.coursestools`, API served under context path `/api`
(`src/main/resources/application.yml:46`), all controllers versioned `/v1/*`.

## Diagram

```
        HTTP  /api/v1/**  (browser / frontend)      Payment webhooks (Stripe, CryptoCloud)
                 |                                              |
        [SecurityConfig + JwtRequestFilter]  (config/security/) |
                 v                                              v
  controller/   12 REST controllers (/v1/authorization, /v1/users, /v1/orders,
                /v1/payments, /v1/subscriptions, /v1/admin, /v1/alerts, ...)
                 |
                 v
  facade/       5 orchestration facades: Auth, Order, Payment, Transaction, User
                (simpler controllers call services directly)
                 |
                 v
  service/      business logic
                 |- payment/impl: StripePaymentService | CryptoPaymentService | BalancePaymentService
                 |- external:     ActivatingSubscriptionService (TV access bot), TradingViewService
                 |                (nickname check), OAuth Google/Discord, GeoLocation, TV retry
                 |- user:         User* aggregate services (data, finance, subscription, tx, social)
                 |
                 v
  repository/   Spring Data JPA (+ specification/ for dynamic filters) --> PostgreSQL
                                                                            Redis <- TokenService
   side channels out of the transaction:
     ApplicationEvents --> listener/ (@Async, mostly @TransactionalEventListener)
        --> TV access bot HTTP, SMTP email, alert bot
     scheduler/ crons --> subscription expiry / trial / grace-period sweeps, TV retry queue poll
```

## Layering rules (as actually practiced)

- **Controller -> Facade -> Service -> Repository.** Facades exist only where a
  request spans multiple services (Auth, Order, Payment, Transaction, User —
  `facade/`). Controllers with a single-service surface (Admin, Alert, Code,
  News, Partnership, Subscription, UserSocial) inject services directly.
- **Events for post-commit side effects.** Services publish Spring
  `ApplicationEvent`s (`event/`: `UserCreateEvent`, `SubscriptionChangeStatusEvent`,
  `UserAlertsChangeEvent`); `SubscriptionChangeStatusEvent` is built via
  `SubscriptionMapper.toEvent(...)` with a `SubscriptionEventType` of CREATED /
  RESTORED / EXTENDED / TRIAL_CREATED / TRIAL_ENDED / GRACE_PERIOD_START /
  GRACE_PERIOD_END. Listeners in `listener/` run `@Async` and (except user-region
  and email sends) `@TransactionalEventListener` after commit, in a
  `REQUIRES_NEW` transaction — e.g. TV access activation in
  `listener/SubscriptionChangeStatusListener.java:50-53`. Email notifications
  share `AbstractNotificationListener` + per-event `messaging/` MessageBuilders.
- **External calls are resilient.** resilience4j `@Retry(name = "default")`
  with durable fallbacks on every external HTTP call (e.g.
  `service/external/ActivatingSubscriptionService.java:29`); permanent TV
  failures land in the `TradingViewRetryJob` queue (DB table) that
  `scheduler/TradingViewRetryScheduler.java:16` polls and admins can manage.
- **Specifications for filtered lists.** `specification/AbstractSpecification`
  (`from(filter) -> Specification<T>`) + `AbstractPredicateBuilder`, used for
  alerts, admin orders, user subscriptions, and the TV retry job list.
- **MapStruct mappers** (`mapper/`, 10 interfaces) for entity<->DTO and
  entity->event mapping; Lombok everywhere.
- **Schedulers** (`scheduler/`): `SubscriptionScheduler` runs three cron sweeps
  (expired, trial-expired, grace-period-expired subscriptions —
  `scheduler/SubscriptionScheduler.java:19,27,35`), `TradingViewRetryScheduler`
  polls due retry jobs; `OrderScheduler` is an empty stub (TODO only). Cron
  expressions live in `src/main/resources/configs/scheduler.yml`; other config
  splits (`partnership.yml`, `payment-platforms.yml`, `emails.yml`,
  `actuator.yml`) are imported in `application.yml:30-35`.

Key domain vocabulary (from `enums/`): tiers ESSENTIALS/PRO, plans
MONTH/YEAR/LIFETIME/TRIAL, subscription statuses PENDING/GRANTED/GRACE_PERIOD/
TERMINATED, payment methods CRYPTO/STRIPE/PAYEER/BALANCE/MANUAL — note PAYEER
is retired: its validator rejects all new payments
(`validation/validator/payment/impl/PayeerPaymentValidator.java:21`).

## Subsystem index

| Subsystem | File | What's in it |
|---|---|---|
| Product | `../product/overview.md` | What CT-Pro is: tiers (PRO/ESSENTIALS), plans, 7-day trial, what users buy and get |
| Subscriptions + TradingView | `subscriptions-and-tradingview.md` | Subscription lifecycle/statuses, grace period, TV access bot calls, nickname verification, TV retry queue |
| Payments | `payments.md` | Orders/pricing, Stripe (primary, webhooks + lifecycle sync), CryptoCloud, Balance, retired Payeer |
| Auth + security | `auth-and-security.md` | JWT filter chain, OAuth Google/Discord, stateless refresh JWT in HttpOnly cookie, signup TV-nickname check |
| Partnership, referrals, alerts | `partnership-referrals-alerts.md` | 11-level referral cashback, partner codes (PRO-only), alerts + alert bot push |
| Admin | `admin.md` | `/v1/admin` surface: manual grants (classic/custom/lifetime), TV retry admin, invoices/stats |
| Persistence, scheduling, events | `persistence-scheduling-events.md` | Entities/repositories, Flyway migrations, Redis usage, schedulers, event/listener machinery |

(One deep-dive file per subsystem. NEVER name a subsystem file
after a plan — `discovery.md`, NOT `plan-007-discovery.md`. Plans
are temporary; subsystems are forever.)

## Subsystems that have NOT yet earned a file

Foundational pieces where code-as-docs is still accurate can be
listed here with a code pointer instead of a file:

- News (static CMS-ish list) — `controller/NewsController.java`, `service/NewsService.java`
- Discount codes — `controller/CodeController.java`, `service/CodeService.java`
- Email delivery — `service/EmailService.java` + builders in `messaging/`
- GeoLocation (signup country lookup) — `service/external/GeoLocationService.java`
- Global error handling — `exception/GlobalExceptionHandler.java`

A subsystem earns a file at its first architectural drift (the code
alone no longer explains the shape) — written **in the same commit**
as the change that caused the drift.

## How to add a new subsystem doc

1. New `.md` file in this directory.
2. Add a row above.
3. Cross-link from the relevant code package's top-of-file comment
   (`see ../../llm-corner/docs/architecture/<file>.md`).
4. If the new subsystem implies a new contract, also update
   `../conventions/contracts.md` in the same commit.

## What is NOT architecture

Vendor-API ground truth (field shapes of third-party responses) is
NOT here — it lives in `../reference/` as dated probe docs.
Historical evaluations that led to a vendor choice live in
`../../decisions/`.
