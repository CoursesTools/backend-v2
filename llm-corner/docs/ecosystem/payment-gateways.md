# Payment gateways

Stripe (live, primary) and CryptoCloud; Payeer is retired. Full payment
domain flow lives in `../architecture/payments.md` — this file is the
external-contact-surface view: what we call, what calls us, where secrets are.

## Stripe (live)

| What | Where |
|---|---|
| SDK | `com.stripe:stripe-java`, initialized in `service/payment/impl/StripePaymentService.java:71-74` |
| Webhook entrypoint (they call us) | `POST /api/v1/payments/stripe` (`controller/PaymentController.java:31-37`), `permitAll` (`config/security/PublicUrlsHolder.java:20`), authenticated by `Stripe-Signature` verification against the webhook secret (`StripePaymentService.java:286-296`) |
| Secrets | `.env:39-41`: `STRIPE_SECRET` (live `sk_live_…`), `STRIPE_WEBHOOK_SECRET`, `STRIPE_COUPON` → bound via `src/main/resources/configs/payment-platforms.yml:2-5` into `config/props/StripeProperties.java` |

**Webhook events we consume** (`StripePaymentService.processWebhook:223-249`
and `facade/PaymentFacade.retrieveStripePayment:33-43`):

- `invoice.payment_succeeded` — order fulfillment / subscription renewal;
  fetches the Stripe Subscription to mirror `current_period_end` into
  `users_subscriptions.expired_at` (`StripePaymentService.processPayment:299+`).
- `checkout.session.completed` — only `mode=payment` (one-time custom
  invoices); subscription-mode sessions are ignored (paid via invoice event).
- `customer.subscription.updated` / `customer.subscription.deleted` —
  lifecycle sync (`processSubscriptionLifecycleWebhook:262-280`) → backend
  subscription status/expiry sync (source of truth = Stripe). Everything else
  is logged and ignored.

**What we call on Stripe:** checkout sessions (subscription + one-time,
`createPaymentLink`, `createCustomInvoice`), billing-portal sessions
(`getStripePanel:76-89`, exposed at `GET /api/v1/payments/stripe/panel`),
coupon creation (percentage/fixed, lines 112-155), subscription cancel
(`cancelSubscription:91-103`).

## CryptoCloud

| What | Where |
|---|---|
| Invoice create (we call them) | `POST https://api.cryptocloud.plus/v2/invoice/create` (`service/payment/impl/CryptoPaymentService.java:33`), `Authorization` header from `CRYPTO_API_KEY`, shop id from `CRYPTO_SHOP_ID` |
| Postback entrypoint (they call us) | `POST /api/v1/payments/crypto` — accepts BOTH `application/x-www-form-urlencoded` and `application/json` (`controller/PaymentController.java:43-55`), `permitAll` (`PublicUrlsHolder.java:19`) |
| Postback auth | JWT `token` field verified HS256 against `CRYPTO_SECRET` via `util/jwt/impl/CryptoJwtTokenUtil.java:9-14` (`payment-platforms.crypto.secret`) |
| Secrets | `.env:43-45`: `CRYPTO_SECRET`, `CRYPTO_SHOP_ID`, `CRYPTO_API_KEY` → `configs/payment-platforms.yml:6-9` |

Postback payload: `dto/payment/crypto/CryptoRetrieveDto.java:25-56` —
`invoice_id`, `token`, `order_id`, optional `status` / `amount_crypto` /
`currency` / `invoice_info`; snake_case setters kept for form binding,
`@JsonProperty`/`@JsonAlias` for JSON. Access is granted only when
`status == "success"` (`CryptoRetrieveDto` doc lines 40-46,
`CryptoPaymentService.STATUS_SUCCESS:32`). Fix history:
`../../history/inbox/MSG-FIX-CRYPTOCLOUD-WEBHOOK.md`.

## Payeer — RETIRED

- `PaymentMethod.PAYEER` still exists in the enum
  (`enums/PaymentMethod.java:6`) for historical rows, but any attempt to pay
  with it throws "Payeer payment method is no longer supported"
  (`validation/validator/payment/impl/PayeerPaymentValidator.java:21`).
- Migration `db/migration/V5__migrate_payeer_to_crypto.sql` converted all
  historical PAYEER orders/subscriptions to CRYPTO.
- `.env:47-48` still carries `PAYEER_SHOP_ID` / `PAYEER_SECRET`; **nothing in
  `src/` reads them** (verified by grep) — safe-to-delete candidates.
- There is no Payeer webhook endpoint in `PaymentController`.

## Balance / Manual (internal, not gateways)

`POST /api/v1/payments/balance` pays from the user's internal partner-cashback
balance (`PaymentController.java:57-65`, `BalancePaymentService`); `MANUAL` is
admin-granted. No external contact surface — see
`../architecture/payments.md`.

## Binding invariants on our side

- Both webhook endpoints are unauthenticated at the Spring Security layer;
  the ONLY protection is Stripe signature / CryptoCloud JWT verification.
  Never remove those checks or add the paths to any auth bypass beyond
  `PublicUrlsHolder`.
- Stripe is the expiry source of truth for Stripe-paid subscriptions:
  `expired_at` must mirror `current_period_end` exactly (see
  `../architecture/payments.md`); expiry changes are re-synced to the TV
  access bot (`tv-access-bot.md`).
- CryptoCloud postbacks may arrive in either content type; keep both handler
  overloads when touching `PaymentController`.

## Sending work to their agents

Third-party SaaS — no agent inboxes. Stripe dashboard/webhook config is
operator-managed (live mode). TODO(operator): note where the Stripe webhook
endpoint configuration + CryptoCloud shop settings are administered.

## Change history

- 2026-07-12 — file created from verified code.
