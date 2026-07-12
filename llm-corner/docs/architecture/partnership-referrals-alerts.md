# Partnership (Referrals) & Alerts

Two independent subsystems documented together because both are small.
Partnership = referral cashback + partner codes. Alerts = user-configurable
TradingView alert subscriptions synced to an external Telegram alert bot.

---

## Subsystem 1: Partnership / Referrals

### Purpose

Multi-level referral program: every user gets a personal partner code; when a
referred user pays, the referrer (and the referrer's referrer) earn cashback
onto their internal balance, which is spendable (Balance payment method) or
withdrawable.

### Partner codes

- Created for EVERY user at signup (`AuthService.setupAndSaveUser`,
  `service/AuthService.java:124`): 7-char hex slug from a UUID
  (`util/StringGeneratorUtil.java:17-22`), stored as a `Code` row with
  `owner = user`, `tier = PRO`, `discountType = PERCENTAGE`,
  `discountValue = partnership.discount` (30%) —
  `service/CodeService.java:41-50`.
- A `Code` is a "partnership code" iff `owner != null`
  (`entity/Code.java:71-73`); owner-less codes are admin promo codes.
- **PRO-only**: `Code.tier = PRO` means the code is rejected on orders for any
  other tier (`service/OrderService.java:75-79`,
  `service/CodeService.java:92-101`). Pre-existing partner codes were
  backfilled by `db/migration/V9__partner_codes_pro_only.sql`.
- Usage validation (`validation/validator/CodeValidator.java:132-158`): max
  uses / expiry; for partner codes additionally: cannot use if you already
  have a different referrer, cannot use your own code, no mutual curatorship
  (A refers B ⇒ B cannot refer A).

### Referral registration (`referrals` table, `entity/Referral.java`)

- Fields: `referrer`, `referred` (unique — one referrer per user),
  `isActive`, `isBonusUsed`.
- Created two ways:
  1. Signup with `referrerCode` → `registerReferral(..., isActive=false)`
     (`service/AuthService.java:121-123`, `service/ReferralService.java:38-41`).
  2. Paying an order that carries a partner code, when the payer has no
     referrer yet → `registerReferral(owner, user, isActive=true)` inside
     `CodeService.useCode` (`service/CodeService.java:69-80`).
- Activation: referral flips to `isActive=true` on the referred user's first
  successful payment (`service/OrderService.java:168-170`).
- Referred-user bonus: if a user has a referrer and `isBonusUsed=false`, the
  referrer's partner code (30% off) is auto-applied at order creation even
  without typing it (`service/OrderService.java:84-90`); `isBonusUsed` flips
  after payment (`service/OrderService.java:171-173`), so the discount is
  one-time. Cashback for the referrer, by contrast, is recurring (see below).

### Levels & rates — `src/main/resources/configs/partnership.yml`

- 11 levels, ranks 0–10: START_5 (14%/7%) → KING_50000 (33%/16%). Each level:
  `required-referrals`, `cashback-1` (direct referral), `cashback-2`
  (referral-of-referral). Bound to `PartnershipProps`
  (`config/props/PartnershipProps.java`), sorted by rank at `@PostConstruct`.
- `discount: 30` — the partner-code discount percent.
- `permitted-users-for-referral-cashback` (env
  `PERMITTED_USERS_FOR_REFERRAL_CASHBACK`, currently user id 710): referrer
  ids whose referrals earn cashback even when the order used a
  NON-partnership promo code (`service/OrderService.java:52-53,161-164`).
- Level state lives in `user_partnership` (`entity/user/UserPartnership.java`):
  `level` (rank), `termsAccepted`, `customCashback1/2` (per-user admin
  overrides that beat the level rate — `PartnershipService.resolveRate`,
  `service/PartnershipService.java:100-103`).
- Level-up: `recalculateLevelAfterNewReferral` counts ACTIVE referrals and
  bumps one level when count >= current level's `required-referrals`
  (`service/PartnershipService.java:32-44`). Only invoked from
  `CodeService.useCode`, i.e. when a partner code is consumed by a payment.

### Cashback flow (the money path)

Trigger: `OrderService.processSuccessfulPayment`
(`service/OrderService.java:160-174`) — runs on EVERY successful payment of a
referred user, including Stripe recurring renewals, provided the order used no
code, a partnership code, or the referrer is in the allowlist.

`PartnershipService.calculateCashbackAfterNewReferral`
(`service/PartnershipService.java:69-87`), 2 levels deep
(`CASHBACKS_LEVELS_LIMIT = 2`):

1. Direct referrer earns `amount * cashback1(referrer's level or custom)%`,
   credited to `UserFinance.balance`, recorded in `referrals_earnings`
   (native insert, `repository/ReferralRepository.java:29-34`).
2. The earned amount is subtracted, then the referrer's own referrer earns
   `remainder * cashback2(their level or custom)%`.

All money is integer cents (`earn.intValue()`; display divides by 100 —
`service/user/UserTransactionService.java:76-79`).

Withdrawal: `UserTransactionService.withdraw`
(`service/user/UserTransactionService.java:82-96`) — balance check, negative
balance mutation, `WITHDRAWAL` transaction, POST to `urls.withdrawal` = env
`WITHDRAWAL_URL` (TV access bot `http://45.141.184.24:4320/withdrawal`).

### API surface

- `GET /api/v1/partnerships/levels` — level table (`controller/PartnershipController.java`).
- `GET /api/v1/users/me/partnership` — level names, referral counts, earnings
  per cashback level, partner code, `termsAccepted`, effective rates, curator
  Discord (`service/PartnershipService.java:46-64`).
- `GET /api/v1/users/me/partners` — paged referral list with per-referral
  profit (`repository/ReferralRepository.java:36-59`).
- `PATCH /api/admin/users/partnership/cashback` (ADMIN) — set custom rate
  overrides (`controller/AdminController.java:83-86`).
- `termsAccepted` is set via `PATCH /api/v1/users/me` (`mapper/UserMapper.java:62`).

### Gotchas

- `requiredReferralsForNextLevel` in the partnership DTO is the CURRENT
  level's threshold (that is what unlocks the next level) —
  `service/PartnershipService.java:52`.
- Level recalc happens only on partner-code consumption; a signup-referred
  user activating via first payment does trigger it in practice because the
  referrer's code is auto-applied to that first order, but later payments
  never recalc.
- Withdrawal `@Retry(name="default")` sits on a private self-invoked method,
  so the retry aspect never applies (comment at
  `service/user/UserTransactionService.java:98-104`).

---

## Subsystem 2: Alerts

### Purpose

Users subscribe to a catalog of TradingView alert definitions; an external
Telegram alert bot delivers them. The backend owns the catalog + user
subscriptions and pings the bot whenever a user's set changes.

### Model

- `Alert` (`alerts` table, `entity/Alert.java:36-49`): `type`, `broker`, `tf`
  (timeframe), `event`, `asset`, `indicator`, `multiAlert`. Catalog rows.
- `UserAlert` (`users_alerts`, `entity/user/UserAlert.java`): composite PK
  (user_id, alert_id) + `jsonb properties` (per-subscription settings sent by
  the frontend).

### Access rules (`AlertService.checkUserSubscription`, `service/AlertService.java:231-241`)

Every alert endpoint requires an active COURSESTOOLS subscription AND a linked
Telegram id; otherwise 409. Tier gating: indicators allowed per tier live in
`tier_indicator_permissions`; ESSENTIALS is restricted, and a tier with no
rows is unrestricted (`validation/validator/AlertValidator.java:111-141`).
Subscribe requests to forbidden indicators throw; the categories endpoint
intersects tier-allowed indicators with the requested filter
(`service/AlertService.java:181-229`).

### Endpoints (`controller/AlertController.java`, base `/api/v1/alerts`)

- `GET /` — catalog by filter (JPA Specification).
- `GET /categories`, `GET /categories/multi` — available filter dimensions
  (types/assets/brokers/events/timeframes/indicators), single vs multi-alert.
- `GET /me/categories` — categories of the user's own subscriptions.
- `GET /me/subscriptions` — user's subscribed alerts (paged).
- `POST /me/subscriptions` — bulk subscribe by filter; batch insert/update,
  returns affected count (`service/AlertService.java:81-149`).
- `DELETE /me/subscriptions?alertsIds=` — unsubscribe specific (409 if any id
  is not subscribed).
- `DELETE /me/subscriptions/all` — unsubscribe everything.

### Change-notification flow (bot sync)

1. Every subscribe/unsubscribe publishes `UserAlertsChangeEvent(telegramId)`
   inside the transaction (`service/AlertService.java:146,164,172`).
2. `listener/UserAlertChangeListener.java:28-41` —
   `@TransactionalEventListener @Async` (fires after commit) — POSTs an empty
   body to `{urls.alert-bot}?telegramId=<id>`.
3. `urls.alert-bot` = env `ALERT_BOT_URL` =
   `http://193.160.209.73:8080/api/alert/message` (`.env:23`); the bot
   re-syncs that user's alert set on its side.

Other triggers of unsubscribe-all (each also pings the bot):

- Expired subscription + expired trial schedulers
  (`scheduler/SubscriptionScheduler.java:20-33`).
- Unlinking Telegram (`service/user/UserSocialService.java:120-125` —
  unsubscribes BEFORE nulling the id, so the event still carries it).

### Gotchas

- The bot POST has no retry and no error handling; a bot outage silently
  drops the sync (async exception only reaches the executor's logger).
- `AbstractNotificationListener` offers email fan-out, but
  `UserAlertChangeListener` never calls `sendEmails` — no emails on alert
  changes.

## Change history

- 2026-07-12 — created from code audit of PartnershipService/ReferralService/AlertService (docs bootstrap).
