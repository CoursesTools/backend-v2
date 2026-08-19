# Product Overview

CoursesTools (CT) is a subscription SaaS for retail traders. Customers pay for
"CT-Pro" — access to premium, invite-only TradingView indicators. The backend
(this repo, `backend-v2`) is the system of record: it handles accounts, orders,
payments, subscription lifecycle, and a referral/partnership program. It does
not grant TradingView access itself; on every subscription grant/extension it
calls an external "TV access bot" that adds the user's TradingView username to
the private indicators for the paid period
(`src/main/java/com/winworld/coursestools/listener/SubscriptionChangeStatusListener.java:53-64`).

## Who uses it

- **External: retail TradingView traders.** Every account is bound to a real
  TradingView username — signup verifies the nickname exists by fetching
  `https://tradingview.com/u/{name}`
  (`src/main/java/com/winworld/coursestools/service/external/TradingViewService.java:20-38`,
  called from `service/AuthService.java:108`).
- **External: partners/affiliates** — users who share partner codes and earn
  two-level referral cashback (see roles below).
- **Internal: admins** — grant/extend subscriptions manually, manage the TV
  retry queue, view stats (`controller/AdminController.java`,
  `controller/SubscriptionController.java:43-47`).

## What it does (high level)

1. **Auth & identity** — JWT auth with refresh tokens, email/password plus
   Google/Discord OAuth; TradingView nickname required and verified at signup
   (`controller/AuthController.java:31-104`).
2. **Subscriptions** — CT-Pro with tiers/plans (below), a 7-day free trial
   (`src/main/resources/application.yml:103`), and a 7-day grace period after
   expiry (`service/SubscriptionService.java:74`). Statuses: `PENDING`,
   `GRANTED`, `GRACE_PERIOD`, `TERMINATED` (`enums/SubscriptionStatus.java:4`).
3. **Payments** — Stripe is primary (checkout + subscription lifecycle
   webhooks at `POST /v1/payments/stripe`); CryptoCloud postbacks at
   `POST /v1/payments/crypto`; internal balance payments at
   `POST /v1/payments/balance` (`controller/PaymentController.java:31-65`).
   Prices are stored in cents and converted to USD by dividing by 100
   (`service/payment/PaymentService.java:19-23`).
4. **Partnership** — referred buyers get a 30% discount
   (`src/main/resources/configs/partnership.yml:3`); partners earn two-level
   cashback that scales across ranks 0–10 (START_5 → KING_50000: level-1
   cashback 14%→33%, level-2 7%→16%, `configs/partnership.yml:5-59`). Partner
   codes are generated PRO-tier only (`service/CodeService.java:41-49`).
   Cashback accrues to a balance withdrawable via an external withdrawal
   service (`service/user/UserTransactionService.java:39-40`).
5. **Alerts** — user-configurable alert subscriptions under `/v1/alerts`
   (`controller/AlertController.java:27-87`), delivered through an external
   alert bot (`urls.alert-bot`, `application.yml:85`).

## Core flow (the value loop)

1. User signs up with email + TradingView nickname; nickname existence is
   verified against tradingview.com (`service/AuthService.java:108`).
2. User starts a 7-day trial (`POST /v1/subscriptions/start-trial`,
   `controller/SubscriptionController.java:28-31`) — a $0 PRO-monthly
   subscription, once per TradingView username
   (`service/SubscriptionService.java:120-151`) — or creates an order
   (`POST /v1/orders`, `controller/OrderController.java:33-39`) and pays via
   Stripe / CryptoCloud / balance.
3. The payment webhook/postback activates or extends the `UserSubscription`
   and publishes a `SubscriptionChangeStatusEvent`
   (`service/SubscriptionService.java:197-203`).
4. An async listener calls the TV access bot to grant indicator access for the
   user's TradingView username, tier, and expiry
   (`listener/SubscriptionChangeStatusListener.java:53-64`). Failures go
   through resilience4j retry, then a durable DB retry queue drained by a
   scheduler; permanent "nickname not found" errors surface on the admin TV
   retry page
   (`service/external/ActivatingSubscriptionService.java:29-107`).
5. On expiry, a scheduler moves the subscription to `GRACE_PERIOD` (7 days),
   then `TERMINATED`; the TV bot is told to withdraw access. Paying again
   during grace restores access (`scheduler/SubscriptionScheduler.java:35-39`,
   `service/SubscriptionService.java:200-202`).

## Tiers and plans

Two tiers (`enums/SubscriptionTier.java:6`) x plans `MONTH`, `YEAR`,
`LIFETIME`, plus internal `TRIAL` (`enums/Plan.java:4`). Seed prices (cents,
i.e. USD/100):

| Tier | MONTH | YEAR | LIFETIME | Source |
|---|---|---|---|---|
| PRO | $29.99 | $289.99 | $480.00 | `db/migration/V2__insert_plans.sql:8-10` |
| ESSENTIALS | $14.90 | $119.50 | $199.30 | `db/migration/V6__add_tiers_and_essentials.sql:12-14` |

- **PRO** — unrestricted indicator access (no allowlist rows = unrestricted,
  `V6__add_tiers_and_essentials.sql:16-17`); required for partner codes.
- **ESSENTIALS** — restricted by the `tier_indicator_permissions` allowlist;
  currently only the `wcsmc` indicator
  (`V6__add_tiers_and_essentials.sql:28-30`).
- **LIFETIME** — no recurring billing; stored with sentinel expiry
  `2100-12-31` and an `isLifetime` flag for the TV bot
  (`service/SubscriptionService.java:75`).
- Monthly price after discounts may not drop below $10
  (`validation/validator/OrderValidator.java:49-58`).
- Subscription product families: `COURSESTOOLS` (the live product) and
  `MENTORSHIP` (`enums/SubscriptionName.java:6-7`; a Mentorship monthly plan
  is seeded in `V2__insert_plans.sql:11` but no service logic references it).

## User roles

Roles: `USER`, `ADMIN`, `PARTNER` (`enums/UserRole.java:4`).

- **USER** — buys/renews subscriptions, manages alerts, uses promo codes.
- **PARTNER** — a user with a partner code; earns referral cashback and can
  withdraw balance.
- **ADMIN** — `@PreAuthorize("hasRole('ADMIN')")` endpoints: grant classic /
  custom / lifetime / trial subscriptions, user search, stats, TV retry-queue
  administration (`controller/AdminController.java`,
  `controller/SubscriptionController.java:43-47`).

## What it does NOT do (and why)

- **No direct TradingView access management.** Granting/revoking indicator
  access and TradingView renames are delegated to an external TV access bot
  over HTTP (`service/external/ActivatingSubscriptionService.java:23-27`).
  The backend only checks that a nickname exists on tradingview.com.
- **No live Payeer flow.** `PAYEER` exists in `enums/PaymentMethod.java:6` and
  has a validator, but there is no Payeer `PaymentService` implementation and
  no controller endpoint — only Stripe, Crypto, and Balance are implemented
  (`service/payment/impl/`).
- **`MANUAL` is not a gateway** — it marks admin-granted subscriptions, not a
  payment flow.
- **No self-serve Mentorship product.** The `MENTORSHIP` subscription type is
  seeded in the DB but has no business logic wired to it.
- **No indicator content.** Indicators live on TradingView; this backend only
  controls who may access them and for how long.

## Tech stack (one line)

Java 17, Spring Boot 3.2.4, PostgreSQL (Flyway), Redis, Gradle; layered
Controller → Facade → Service → Repository with async application events;
API base path `/api`. See `../architecture/README.md` for the full picture.
