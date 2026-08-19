# Payments & Orders

How a user's money becomes an active CT-Pro subscription: order creation,
the four payment providers, webhook/payment retrieval, and how a paid order
updates `user_subscriptions`. Subscription statuses/tiers themselves are the
subscriptions doc's topic; TradingView activation mechanics are the TV doc's.

All money values are **cents** stored as `BigDecimal` (`Order.originalPrice`
/ `Order.totalPrice`, `entity/Order.java:49-53`). Providers that need USD
divide by 100 (`service/payment/PaymentService.java:19-23`; Stripe passes
cents straight to `setUnitAmount`, `StripePaymentService.java:481`).

## Flow overview

```
POST /api/v1/orders                          payment webhook / call
  OrderController -> OrderFacade               PaymentController -> PaymentFacade
    OrderService.createOrder                     provider.processPayment -> ProcessPaymentDto{orderId, providerData}
      (order PENDING, validators)                  OrderService.processSuccessfulPayment   [row lock]
    provider.createPaymentLink                       SubscriptionService.updateUserSubscriptionAfterPayment
  <- ReadOrderDto{paymentLink}                       transaction + referral cashback; order -> PAID
                                                     event CREATED/EXTENDED/RESTORED
                                                       -> SubscriptionChangeStatusListener (async)
                                                          TV access bot activate; sub -> GRANTED
```

## Order lifecycle

- Statuses: `PENDING` -> `PAID` only (`enums/OrderStatus.java`). No failed/
  canceled status — an unpaid order just stays PENDING forever.
- `OrderType`: `RECURRENT` iff paymentMethod is STRIPE, else `ONE_TIME`
  (`service/OrderService.java:104-105`). A RECURRENT order is reused by every
  Stripe renewal invoice — it is re-processed while already PAID.
- Creation (`OrderService.createOrder`, `service/OrderService.java:55-113`):
  resolves plan, carries over the user's old monthly price when re-buying the
  same MONTH plan (`:65-67,202-207`), applies promo code (tier-restricted,
  `:75-79`) or one-time referral-bonus discount (`:83-89`) via
  `PricingService.calculatePrice`, then runs `OrderValidator` plus the
  method-specific `PaymentValidator` list (`:108-110`).
- Per-method create validators (`validation/validator/payment/impl/`):
  - Stripe: no LIFETIME/TRIAL plans; no second Stripe sub while one is active
    (`StripePaymentValidator.java:25-36`).
  - Balance: must have sufficient balance (`BalancePaymentValidator.java:25-29`).
  - Payeer: always throws "no longer supported" (`PayeerPaymentValidator.java:20-22`).
- `OrderFacade.createOrder` (`facade/OrderFacade.java:36-43`) then asks the
  matching `PaymentService` impl for a payment link and returns it in the DTO.

## Payment methods

`enums/PaymentMethod.java`: `CRYPTO, STRIPE, PAYEER, BALANCE, MANUAL`.
Each live provider extends `PaymentService<T>` (`service/payment/PaymentService.java`)
with `createPaymentLink` / `getPaymentMethod` / `processPayment`.

| Method | Service | Pay-in | Retrieval endpoint (`/api/v1/payments`) |
|---|---|---|---|
| STRIPE (primary) | `impl/StripePaymentService.java` | Checkout Session, mode=SUBSCRIPTION | `POST /stripe` webhook (unauthenticated, signature-verified) |
| CRYPTO (CryptoCloud) | `impl/CryptoPaymentService.java` | `api.cryptocloud.plus/v2/invoice/create`, 24h TTL | `POST /crypto` postback (form-urlencoded or JSON) |
| BALANCE | `impl/BalancePaymentService.java` | none (internal) | `POST /balance?order_id=` (JWT-authenticated user call) |
| PAYEER | — none | dead: blocked at order creation | legacy enum value only |
| MANUAL | — none | admin grants / lifetime; never an order flow | — |

`/v1/payments/stripe` and `/v1/payments/crypto` are in the public URL list
(`config/security/PublicUrlsHolder.java:19-20`); balance is a logged-in call.

### Stripe (primary)

- Link = Checkout Session in SUBSCRIPTION mode, `order_id` stamped into both
  session and subscription metadata (`StripePaymentService.java:434-462`).
  YEAR plan -> yearly recurring interval, everything else monthly (`:470-485`).
  Discounts: partnership code -> the fixed coupon from
  `payment-platforms.stripe.coupon`, other promo codes -> a Stripe coupon
  named `CODE.toUpperCase()` (`:487-502`; coupons are created via
  `createPercentageCoupon`/`createFixedAmountCoupon`, `:112-155`).
- Webhook (`PaymentFacade.retrieveStripePayment`, `facade/PaymentFacade.java:33-52`),
  signature verified against `webhookSecret` on every parse (`StripePaymentService.java:286-296`):
  - `customer.subscription.updated` / `.deleted` -> lifecycle sync (below).
  - `invoice.payment_succeeded` -> `processPayment` (`:298-321`): extracts
    customerId/invoiceId, and for subscription invoices calls
    `Subscription.retrieve` to capture `currentPeriodEnd`; order id read from
    invoice line -> invoice -> subscription metadata (`:341-353`).
  - `checkout.session.completed` with mode=payment -> `processCheckoutSession`
    (`:329-339`) — used by admin custom invoices (one-time PAYMENT-mode
    sessions, `service/AdminInvoiceService.java:36-73`).
  - Anything else is logged and ignored (`:242-245`).
- Also owns: customer billing portal (`GET /v1/payments/stripe/panel`,
  non-trial Stripe subs only, `facade/PaymentFacade.java:68-81`) and
  `cancelSubscription` used when a user switches away from Stripe.

### CryptoCloud

- Postback carries a JWT `token`; its `id` claim must match the body
  `invoice_id` and be signed with `CRYPTO_SECRET` (`CryptoPaymentService.java:124-148`).
- Only `status=success` grants access; other statuses (partial/fail/canceled)
  return null and are ignored; null status accepted for legacy form postbacks
  (`:102-110`). Invoice creation is wrapped in resilience4j `@Retry` with a
  throwing fallback (`:54,162-172`).

### Balance

- `processPayment` checks the caller owns the order, then decreases the
  user's internal balance by `totalPrice` (`BalancePaymentService.java:42-56`).
  `createPaymentLink` returns null — there is nothing to redirect to (`:30-33`).

## Processing a paid order

`OrderService.processSuccessfulPayment` (`service/OrderService.java:115-179`),
transactional, order row locked via `findByIdForUpdate`
(PESSIMISTIC_WRITE, `repository/OrderRepository.java:17-19`):

1. Already-PAID guard: throws unless the order is RECURRENT (Stripe renewals
   legitimately re-hit the same order, `:122-127`).
2. Renewal payments are booked at `originalPrice` (undiscounted); first
   payments at `totalPrice` (`:128`).
3. Stripe renewal dedupe: skip if the incoming `invoiceId` equals the one
   already stored on the subscription's `paymentProviderData` (`:138-145,181-191`).
4. Promo code marked used (first payment only), subscription updated (next
   section), a `PURCHASE` user transaction written, referral cashback
   calculated via `PartnershipService` and the referral activated/bonus
   consumed (`:130-174`), order set PAID (`:175-177`).

## Subscription update & expiry rules

`SubscriptionService.updateUserSubscriptionAfterPayment`
(`service/SubscriptionService.java:189-207`) branches on the user's current
sub for that subscription type and publishes a `SubscriptionEventType`:

| Current sub | Action | Event |
|---|---|---|
| none, or trial | `createNewSubscription` (trial row -> TERMINATED; expiry base = trial's `expiredAt`, else now UTC) `:296-325` | `CREATED` |
| `GRACE_PERIOD` | `updateGracePeriodSubscription` (expiry base = now) `:327-345` | `RESTORED` |
| anything else | `extendExistingSubscription` (expiry base = current `expiredAt`) `:347-372` | `EXTENDED` |

Expiry (`calculateExpirationDate`, `:597-610`):

- **Stripe:** `expiredAt` = the Stripe subscription's `currentPeriodEnd`
  (epoch seconds -> UTC), mirrored exactly. Stripe owns the billing boundary.
- **Everything else:** `expiredAt` = base + `plan.durationDays`.

Switching a Stripe-backed sub to a non-Stripe payment cancels the Stripe
subscription remotely (`:335-337,363-365`). New/restored subs are saved as
`PENDING`; the async `SubscriptionChangeStatusListener` reacts to
CREATED/TRIAL_CREATED/EXTENDED/RESTORED, activates TV access, and flips the
sub to `GRANTED` (`listener/SubscriptionChangeStatusListener.java:50-78`;
a permanently-invalid TV nickname still grants but enqueues a DEAD retry row
for admins).

## Stripe lifecycle sync (non-payment webhooks)

Keeps local state matching Stripe when changes happen outside our flow
(portal cancellations, Stripe retries, dunning):

- `customer.subscription.updated` -> `syncStripeSubscriptionUpdated`
  (`service/SubscriptionService.java:210-233`): looks up the sub by
  `paymentProviderData.subscriptionId`; ignores unknown ids or subs that are
  no longer Stripe-backed; updates `expiredAt` + stores `currentPeriodEnd` /
  `stripeStatus` / `cancelAtPeriodEnd` (`syncStripeLifecycleMetadata`,
  `:270-294`); publishes `EXTENDED` only when `expiredAt` actually changed
  (so TV gets re-synced).
- `customer.subscription.deleted` -> `handleStripeSubscriptionDeleted`
  (`:236-268`): syncs metadata, then (unless already TERMINATED) sets
  `TERMINATED`, deactivates the referral, and publishes `GRACE_PERIOD_END`.
  Note: there is **no bot-revoke channel** — TV access lapses when the
  expiry timestamp already sent to the bot passes (padded +1 day,
  `dto/external/ActivateTradingViewAccessDto.java:20-31`).
- The reverse guard: admin/manual expiry edits on Stripe-backed subs are
  rejected (`ensureNotStripeManaged`, `:630-636`).

## Gotchas

- A Stripe RECURRENT order is the only order that gets processed more than
  once; invoice-id dedupe is the sole protection against double-extension
  from webhook replays.
- `paymentProviderData` (jsonb on `user_subscriptions`) is **replaced**, not
  merged, on payment (`:342,370`) — lifecycle sync merges (`:274-277`).
- PAYEER survives only as an enum value for historical rows; creating a
  Payeer order 400s at validation.
- MANUAL is never a user-facing method — admin grants build a transient
  never-persisted Order to reuse this same update path
  (`SubscriptionService.adminGrantPaid`, `:436-473`), writing no transaction.
- LIFETIME via Stripe is blocked for active subs by `StripePaymentValidator`;
  `buildPriceData` would otherwise bill LIFETIME as monthly recurring
  (`StripePaymentService.java:470-485`).
