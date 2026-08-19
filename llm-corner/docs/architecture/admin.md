# Admin Surface

Back-office control plane: subscription grants, user lookup, order search,
TradingView retry-queue administration, statistics, partnership cashback
overrides, and custom Stripe invoices. Everything lives on
`AdminController` (`/api/v1/admin/**`) plus one admin-only endpoint on
`SubscriptionController` (`POST /api/v1/subscriptions/activate`).
API context path `/api` is set in `src/main/resources/application.yml:46`.

## Authorization

- Every admin endpoint is `@PreAuthorize("hasRole('ADMIN')")`, except
  `GET /statistics`, which also allows `PARTNER`
  (`controller/AdminController.java:49`).
- Roles come from `users.role` (`enums/UserRole.java`: USER / ADMIN /
  PARTNER; default USER, `entity/user/User.java:45`). The JWT filter maps the
  token's role claim to a `ROLE_<role>` authority
  (`config/security/JwtRequestFilter.java:58`). There is no separate admin
  auth mechanism — same JWT as regular users.

## Endpoint map (`controller/AdminController.java`)

| Endpoint | Service method | Purpose |
|---|---|---|
| `GET /statistics?start&end` | `AdminService.getStatistics` | Active users + per-plan counts at both dates, revenue (PURCHASE tx sum), payout (WITHDRAWAL tx sum). ADMIN or PARTNER. |
| `GET /statistics/plans-by-tier?grantedOnly=false` | `AdminService.getActiveSubscriptionsByTierAndPlan` | Tier x plan matrix of active subs. Default counts GRANTED + GRACE_PERIOD; `grantedOnly=true` counts GRANTED only (`service/AdminService.java:122-134`). |
| `GET /statistics/plans-purchased?start&end` | `AdminService.getPurchasedPlansByTier` | Tier x plan counts of PURCHASE transactions whose order is PAID, in the date window (`repository/user/UserTransactionRepository.java:48-59`). |
| `POST /access/classic` | `AdminService.grantClassicAccess` | Grant by plan: MONTH/YEAR/LIFETIME/TRIAL (see below). |
| `POST /access/custom` | `AdminService.updateCustomAccess` | Pure `expiredAt` bump on an existing sub. |
| `POST /access/direct` | `AdminService.directExtendAccess` | Submit an exact TV bot expiration without changing business DB records. |
| `PATCH /users/partnership/cashback` | `AdminService.updatePartnershipCashback` | Set `customCashback1/2` (0-100) on the user's partnership row. |
| `GET /users?userId\|tradingViewName\|email\|partnerCode` | `AdminService.getUserInfo` | Single-user lookup (see below). |
| `POST /invoices/create` | `AdminInvoiceService.createCustomInvoice` | Custom-price Stripe checkout link. |
| `GET /orders` | `AdminOrderService.getOrders` | Paged/filtered order search. |
| `GET /tv-retry/jobs`, `GET /tv-retry/jobs/{id}` | `AdminTradingViewRetryService.list/get` | TV retry-queue inspection. |
| `POST /tv-retry/jobs/{id}/retry`, `DELETE /tv-retry/jobs/{id}` | `AdminTradingViewRetryService.forceRetry/drop` | Force-retry / drop a queue job. |

## Grants

Dispatch on `ClassicGrantDto.plan` in `service/AdminService.java:90-99`; the
grant logic lives in `service/SubscriptionService.java` ("Admin
classic/custom grant surface", line 418+). User is looked up by
TradingView name, case-insensitive
(`service/user/UserDataService.java:51-56`).

**Body** (`dto/admin/ClassicGrantDto.java`): `tradingViewName`, `tier`
(PRO/ESSENTIALS), `plan`, `trialExpiresAt` (`@Future`; required iff
plan=TRIAL), `keepExpirationDate` (boolean; only valid with MONTH/YEAR —
both rules enforced by `@AssertTrue` validators).

- **MONTH/YEAR, default** (`adminGrantPaid`,
  `service/SubscriptionService.java:436-472`): builds a transient,
  never-persisted Order (MANUAL payment method, status PAID) and routes it
  through the canonical `updateUserSubscriptionAfterPayment` path — so
  create / extend / grace-restore / trial-handoff / Stripe-cancel-on-switch
  behave exactly like a real payment. No `user_transactions` row is written
  (grants never count as revenue).
- **MONTH/YEAR, `keepExpirationDate=true`** (`adminSwapTierKeepExpiry`,
  `service/SubscriptionService.java:474-493`): tier/plan swap that leaves
  `expiredAt` untouched (intended for ESSENTIALS -> PRO swaps). Requires an
  existing non-terminated sub (400 otherwise). Cancels the Stripe
  subscription if the sub was STRIPE, then sets MANUAL / `isTrial=false` /
  status PENDING and publishes EXTENDED.
- **LIFETIME** (`adminGrantLifetime`,
  `service/SubscriptionService.java:496-506`): creates a new sub or converts
  the current one. `expiredAt` = 2100-12-31 23:59:59 (`LIFETIME_EXPIRY`,
  line 75); cancels Stripe if applicable; pushes TV access synchronously
  with the lifetime flag; status GRANTED.
- **TRIAL** (`adminGrantTrial`, `service/SubscriptionService.java:514-568`):
  400 if the user already has a non-trial active sub. Extends an existing
  trial or creates a fresh PENDING trial with the caller-supplied expiry.
  The trial plan is pinned to PRO MONTH regardless of requested tier
  (lines 530-536). Writes the `trial_activations` flag so the user cannot
  later self-claim a trial (lines 560-564). Publishes EXTENDED /
  TRIAL_CREATED.

**Custom grant** (`POST /access/custom`,
`dto/admin/CustomAccessUpdateDto.java`): `tradingViewName` + `@Future`
`expiredAt`. Pure expiry bump; tier/plan inherited from the existing sub;
400 if no active sub or if the sub is Stripe-managed
(`ensureNotStripeManaged`, `service/SubscriptionService.java:630-636`).
Publishes EXTENDED (`adminCustomUpdateExpiry`, lines 576-589).

**Direct Extend** (`POST /access/direct`,
`dto/admin/DirectAccessRequestDto.java`): body `tradingViewName` + `@Future
expiredAt`. It requires an existing non-terminated subscription and inherits
email, tier, and lifetime from that row. It sends the exact selected date to
the TV bot without saving the subscription or publishing an event. Response:

```json
{
  "subscriptionId": 4968,
  "tradingViewName": "aryansn484",
  "expiration": "2026-09-19T00:00:00",
  "deliveryStatus": "DELIVERED"
}
```

`deliveryStatus` is `DELIVERED` after bot 2xx or `QUEUED` after durable
transient-failure enqueue. Both mean submitted, never "database updated".
Missing subscription and permanent nickname 404 are friendly 400 responses.
The audit log includes actor id, target user/subscription, date, and outcome.

**Event follow-through:** EXTENDED / TRIAL_CREATED are consumed by
`listener/SubscriptionChangeStatusListener.java` (lines 33-34), which pushes
TV access and flips status to GRANTED (lines 64, 76). If the TV bot call
fails after resilience4j retries, a durable retry job is enqueued instead
(`service/external/ActivatingSubscriptionService.java:76-80`) and the sub is
still marked GRANTED — the failure surfaces on the TV retry admin page.

## Legacy admin activate — `POST /v1/subscriptions/activate`

`controller/SubscriptionController.java:43-47`, ADMIN-only. Body
`{username, expiration}` (`dto/subscription/SubscriptionActivateDto.java`).
Sets `expiredAt = expiration.atStartOfDay()` and pushes TV access
synchronously (lifetime flag derived from the sub's plan); rejects
Stripe-managed subs; does NOT touch status
(`service/SubscriptionService.java:612-628`). Overlaps with
`/access/custom` — that path is the newer one (event-driven, flips status
to GRANTED).

## User lookup — `GET /users`

`service/user/UserDataService.java:21-42`. Exactly one param is used, with
precedence **email > tradingViewName > partnerCode > userId**; 404 if not
found. `partnerCode` finds the partner who OWNS that referral code.
Response `dto/admin/AdminUserReadDto.java`: id, email, tradingViewName,
telegram, countryCode, partnershipLevel, customCashback1/2, balance,
referrerId, createdAt, and all subscriptions (plan, tier, status, price,
paymentMethod, isTrial, `paymentProviderData`, createdAt, expiredAt).

## Custom invoices — `POST /invoices/create`

`service/AdminInvoiceService.java:36-74`. Body
(`dto/admin/CreateCustomInvoiceDto.java`): userId, plan, tier,
customPrice, optional description. Rejects TRIAL (400); 409 if the user's
current sub already has the requested plan. Persists a PENDING ONE_TIME
STRIPE order at the custom price and returns a Stripe Checkout session URL
(`service/payment/impl/StripePaymentService.java:165-198`). NOTE:
`customPrice` goes into Stripe `unit_amount` via `longValue()` — i.e. it is
interpreted as **cents**, fractional part dropped
(`StripePaymentService.java:173`).

## Order search — `GET /orders`

`service/AdminOrderService.java:30-40` +
`specification/order/AdminOrderSpecification.java`. Filters
(`dto/admin/AdminOrderFilterDto.java`): orderId, userId, email,
tradingViewName, status, paymentMethod, tier, orderType, createdFrom/To
(ISO date-time). Spring `Pageable`; page size capped at 100; sort
whitelist: `id, createdAt, totalPrice, status, paymentMethod`.

## TV retry-queue admin — `/tv-retry/*`

`service/AdminTradingViewRetryService.java` is a thin admin wrapper over
`service/external/TradingViewRetryService.java` (the queue itself is
documented with the TradingView integration). Job types: ACTIVATE, RENAME;
statuses: PENDING, DEAD.

- **List/get**: filter by userId/status/type; page size <= 100; sort
  whitelist `id, nextAttemptAt, firstEnqueuedAt, attempts, status, type`
  (`AdminTradingViewRetryService.java:24-27`). Read DTO exposes `payload`
  (raw JSON that will be POSTed to the TV bot), `lastError` (bot HTTP
  status + body, truncated to 2048 chars), `attempts` (resets on
  force-retry) and `forceRetryCount`
  (`dto/admin/TradingViewRetryJobReadDto.java`).
- **Force-retry** (`TradingViewRetryService.forceRetry`,
  `service/external/TradingViewRetryService.java:228-253`): if the target is
  DEAD but a PENDING job exists for the same user+type, the DEAD row is
  deleted and the PENDING one is retried instead. Atomic UPDATE; 404 if the
  job was already processed.
- **Drop** (`drop`, lines 260-265): idempotent single DELETE; 404 if the
  row no longer exists.

## Gotchas

- Admin grants write no transaction rows — revenue stats
  (`/statistics`, `/statistics/plans-purchased`) are unaffected by them.
- Classic, Custom, Direct Extend, and trial grants send exact TV expirations;
  the one-day TV buffer is reserved for actual customer payments (DEC-002).
- All `LocalDate` expiry inputs become `atStartOfDay()` (midnight UTC —
  the app computes time in UTC, `service/SubscriptionService.java:593-595`).
- `ensureNotStripeManaged` guards `/access/custom` and
  `/subscriptions/activate`, but NOT classic grants — those instead cancel
  the Stripe subscription when switching a STRIPE-backed sub.
- `/statistics` `activeUsers` excludes TRIAL plans; the per-plan
  `planDistribution` map still includes TRIAL
  (`service/AdminService.java:64-87`). Both tier x plan matrices skip TRIAL
  and pre-zero every tier/plan cell.
- Trial grants always land on the PRO MONTH plan row even if
  tier=ESSENTIALS was requested.
