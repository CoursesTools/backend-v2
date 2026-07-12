# Payment Webhook Audit - 2026-04-11

## Executive read

I do not think the currently available evidence proves a backend code regression in the Stripe handler. The strongest fact is the timeline: the last confirmed Stripe webhook processed at `2026-04-08 07:42:21 UTC`, while the next Git commits in this repo are on `2026-04-10`. The Apr 7 tier-related code had already been deployed and successfully processed Stripe webhooks after deployment. Unless production was manually redeployed with different env/config between Apr 8 and Apr 9, the break window points harder at Stripe delivery/config/env/network than at Java code.

That said, I did find real webhook-hardening gaps that could either cause Stripe delivery failures under a dashboard/event-subscription mismatch or make recovery dangerous:

- Unsupported Stripe event types returned HTTP 502 via `PaymentProcessingException`. A webhook endpoint should acknowledge intentionally ignored event types with 2xx after signature verification. Stripe's own docs show unhandled event types being logged while still returning success, and their webhook guide says endpoints should quickly return 2xx before complex work.
- Stripe recurrent payments had no invoice id idempotency. A Stripe retry or manual resend of an already-processed invoice could double-extend a recurrent subscription and create duplicate transactions/cashback.
- Stripe order lookup depended only on invoice line metadata. The app writes `order_id` to subscription metadata, so invoice processing should also fall back to invoice/subscription metadata if the line metadata is absent.

I patched those three items locally and tests pass.

## What changed locally

Files touched:

- `src/main/java/com/winworld/coursestools/service/payment/impl/StripePaymentService.java`
- `src/main/java/com/winworld/coursestools/service/OrderService.java`
- `src/test/java/com/winworld/coursestools/service/payment/impl/StripePaymentServiceTest.java`

Behavior changes:

- Stripe `customer.created`, `payment_intent.succeeded`, `payment_method.attached`, etc. are now logged and ignored instead of returning 502, after signature verification succeeds.
- `checkout.session.completed` in subscription mode is logged and ignored instead of silently returning null.
- New Stripe payment data now stores `invoiceId`.
- Recurrent Stripe webhook processing now skips a duplicate invoice id if the user subscription already stores that invoice id.
- Subscription Checkout sessions now also carry `order_id` in session metadata, not only subscription metadata.
- `invoice.payment_succeeded` now looks for `order_id` in invoice line metadata first, then invoice metadata, then retrieved subscription metadata.

Validation:

- `.\gradlew.bat test --tests "com.winworld.coursestools.service.payment.impl.StripePaymentServiceTest"`: passed.
- `.\gradlew.bat test`: passed.

## Independent root-cause ranking

### 1. Stripe endpoint delivery/config problem

Most likely, pending dashboard confirmation.

Check in Stripe Workbench / Developers -> Webhooks:

- Endpoint URL exactly equals `https://api.coursestools.com/api/v1/payments/stripe`.
- Endpoint status is enabled.
- Events include `invoice.payment_succeeded`; `checkout.session.completed` is useful, but this backend currently grants subscription purchases from `invoice.payment_succeeded`.
- Event deliveries from `2026-04-08 07:42 UTC` through `2026-04-09 14:54 UTC` show whether Stripe stopped delivering, got 400 signature errors, got 502 app errors, or got timeouts.
- Mukul Salaria payment `pi_3TKi28GjHMWbNU7A1qR7bUV1` should have a related `invoice.payment_succeeded` event. Its delivery attempts are the single most important artifact.

If the endpoint is disabled, enable it, deploy the patch, then resend one known victim event first and watch backend logs before batch resend.

### 2. Webhook signing secret mismatch

Still plausible.

Compare only prefixes, do not paste full secrets into chat/logs:

```bash
docker exec backend printenv STRIPE_WEBHOOK_SECRET | cut -c1-12
```

Compare with Stripe endpoint signing secret prefix. A mismatch would produce HTTP 400 and no order/subscription changes. A fake curl returning 400 proves the route and signature verifier exist; it does not prove the live secret matches.

### 3. Unsupported Stripe events poisoning endpoint health

Plausible and now patched.

Before the patch, if the dashboard subscribed this endpoint to broad/all events, every unsupported event returned 502. That can create repeated failed deliveries even though the important `invoice.payment_succeeded` handler is correct. The Stripe dashboard delivery log will make this obvious: look for failed event types such as `payment_intent.succeeded`, `charge.succeeded`, `customer.subscription.created`, etc.

### 4. Missing `order_id` on the invoice event

Possible, now more robust.

The old code required `invoice.lines.data[0].metadata.order_id`. If Stripe sends a valid paid invoice where that field is absent but the subscription has `metadata.order_id`, old code would throw `PaymentProcessingException`, return 502, and leave the order PENDING. The patch falls back to invoice and subscription metadata.

Search logs for:

```text
Order ID not found in invoice metadata
Order ID not found in invoice, invoice line, or subscription metadata
```

If those exist after `Processing Stripe webhook: invoice.payment_succeeded`, this was a real Java-side failure.

### 5. Downstream activation bot failure

Possible only if DB says order is PAID but the subscription is still PENDING.

Payment processing creates/updates a `users_subscriptions` row as PENDING, commits the order as PAID, then an async transactional listener calls the TradingView bot and flips the subscription to GRANTED. If the order remains PENDING, the webhook did not reach successful `OrderService.processSuccessfulPayment`. If order is PAID and subscription is PENDING, inspect `SubscriptionChangeStatusListener`, `ActivatingSubscriptionService`, and the bot URL.

## Production checks I would run now

### DB truth check

Run this before resending so you know the victim set:

```sql
SELECT o.id AS order_id,
       o.status,
       (o.total_price::float / 100) AS price_usd,
       o.created_at AS order_created,
       u.email,
       sp.display_name AS plan,
       sp.tier
FROM orders o
JOIN users u ON u.id = o.user_id
JOIN subscription_plans sp ON sp.id = o.plan_id
WHERE o.payment_method = 'STRIPE'
  AND o.created_at >= '2026-04-08 07:42:21+00'
  AND o.status = 'PENDING'
ORDER BY o.created_at;
```

Then check whether any of those users already have PENDING/GRANTED subscriptions:

```sql
SELECT u.id,
       u.email,
       us.status,
       us.is_trial,
       us.expired_at,
       sp.display_name,
       sp.tier
FROM users u
LEFT JOIN users_subscriptions us
  ON us.user_id = u.id
 AND us.status IN ('GRANTED', 'PENDING', 'GRACE_PERIOD')
LEFT JOIN subscription_plans sp ON sp.id = us.plan_id
WHERE u.id IN (
  SELECT DISTINCT o.user_id
  FROM orders o
  WHERE o.payment_method = 'STRIPE'
    AND o.created_at >= '2026-04-08 07:42:21+00'
    AND o.status = 'PENDING'
);
```

### Logs to inspect

In Loki/Grafana, the split I care about is:

- `{compose_service="backend"} |= "Processing Stripe webhook"`
- `{compose_service="backend"} |= "Forwarding payment to OrderService"`
- `{compose_service="backend"} |= "Order " |= "processed successfully"`
- `{compose_service="backend", level="ERROR"} |~ "(?i)(stripe|payment|signature|webhook|order id|subscription|activating)"`

Interpretation:

- No `Processing Stripe webhook` for Mukul's time means Stripe did not reach the Java handler.
- `Processing Stripe webhook` but no `Forwarding payment` means Stripe handler threw or returned null.
- `Forwarding payment` but no successful order log means `OrderService` or downstream DB/subscription logic threw.
- Successful order log but subscription still PENDING means async activation failed later.

## Recovery sequence

1. Deploy the patch first if possible. It reduces resend risk and prevents unsupported Stripe event types from causing new 502s.
2. Confirm Stripe endpoint URL/status/signing secret/events in dashboard.
3. Pick exactly one victim event, preferably Mukul's related `invoice.payment_succeeded`.
4. Resend it from Stripe dashboard.
5. Watch logs for `Processing Stripe webhook`, `Forwarding payment`, and `Order ... processed successfully`.
6. Re-run the DB victim query.
7. Batch-resend only after one event works.

Do not blindly resend all Stripe events if the endpoint is subscribed broadly. Resend payment/subscription fulfillment events, primarily `invoice.payment_succeeded`, and use dashboard delivery status to avoid reprocessing events that already returned 2xx.

## CryptoCloud notes

The Apr 10 CryptoCloud patch looks directionally correct: it accepts both JSON and form postbacks, maps snake_case fields, requires/sets `currency=USD`, filters non-success statuses, and logs signature failures clearly.

For CryptoCloud production, verify:

- Notification URL is `https://api.coursestools.com/api/v1/payments/crypto`.
- Dashboard payload mode matches what you expect; current backend accepts both `application/json` and `application/x-www-form-urlencoded`.
- Dashboard SECRET KEY prefix matches backend `CRYPTO_SECRET` prefix.
- Postback `status` for paid invoices is exactly success-like. Current backend only grants access when status is absent or equals `success` case-insensitively.

## Remaining hardening I would do next

- Add a durable `payment_webhook_events` table keyed by provider + event id or provider + invoice id. The current invoice-id check is a pragmatic guard, but a DB-level unique constraint is the real fix.
- Return 2xx immediately from Stripe controller after signature verification and queue internal processing. Stripe explicitly recommends quick 2xx acknowledgement before complex work.
- Add an alert: no `Processing Stripe webhook` logs for 6 business hours, or any Stripe delivery status non-2xx in dashboard/API.
- Add a small admin/reconciliation endpoint or script that can safely process a known Stripe invoice id once, with idempotency, instead of relying on dashboard resend.
- Never commit or paste live secrets. The local `.env` contains live-looking payment keys; treat that file as sensitive and rotate if it has been shared outside the trusted machine.

## Follow-up: Caddy routing evidence

New production evidence from Apr 11 points to a concrete ingress-level failure mode:

- `coursestools.com` currently sends all unmatched requests to `frontend:3000`.
- The only explicit `coursestools.com` API route shown is `/api/metrics*`; there is no `/api/v1/payments/*` backend route on the main domain.
- A live test to `https://coursestools.com/api/v1/payments/stripe` returned a Next.js `404 Not Found`, not a Spring/backend response.
- A live test to `https://coursestools.com/api/v1/payments/crypto` also returned a Next.js `404 Not Found`.
- The Caddy log check for Apr 09 14:40-15:10 UTC did not show payment webhook paths. The visible noise was mostly `/actuator/prometheus` client/stream closes against `backend:8081`, not payment traffic.

This means: if Stripe or CryptoCloud webhook URLs are set to the main domain (`https://coursestools.com/api/v1/payments/...`), the immediate cause is found. Those webhooks are being routed to the frontend and will never reach Spring.

The endpoint that should work with the current Caddyfile is:

- `https://api.coursestools.com/api/v1/payments/stripe`
- `https://api.coursestools.com/api/v1/payments/crypto`

Also note:

- `api.winworldteam.com` directly proxies only `/api/v1/payments/stripe` to backend.
- `api.winworldteam.com/api/v1/payments/crypto` is not directly proxied; it falls through to a permanent redirect to `api.coursestools.com`.
- Webhook providers should not depend on redirects. The safest Caddy config is to directly proxy legacy webhook paths instead of redirecting them.

Confirmed current CryptoCloud webhook URL:

- `https://api.winworldteam.com/api/v1/payments/crypto`

With the pasted Caddyfile, that path is not covered by the only payment `handle` under `api.winworldteam.com`, because that handle matches only `/api/v1/payments/stripe`. Therefore CryptoCloud is either receiving the fallback permanent redirect to `https://api.coursestools.com/api/v1/payments/crypto`, or, depending on Caddy's adapted handler order, the broad redirect may be taking precedence even earlier. Caddy documents `permanent` redirects as HTTP 301 and places `redir` early in directive order. A webhook URL should not require a provider to follow a 301 redirect for a POST body.

Live Apr 11 tests confirmed the broad redirect is currently taking precedence:

- `POST https://api.winworldteam.com/api/v1/payments/crypto` returned `301 Moved Permanently` to `https://api.coursestools.com/api/v1/payments/crypto`.
- `POST https://api.winworldteam.com/api/v1/payments/stripe` also returned `301 Moved Permanently` to `https://api.coursestools.com/api/v1/payments/stripe`, despite the apparent Stripe `handle` in the Caddyfile.
- `POST https://api.coursestools.com/api/v1/payments/stripe` reached backend and returned a backend JSON error for missing `Stripe-Signature`.
- `POST https://api.coursestools.com/api/v1/payments/crypto` reached backend/security and returned `403 Forbidden` for the empty test payload.

This makes `api.winworldteam.com` webhook URLs unsafe in the current Caddy config. They are redirects, not direct backend webhook endpoints.

After moving the redirect into a fallback `handle`, live Apr 11 tests changed as desired:

- `POST https://api.winworldteam.com/api/v1/payments/crypto` now returns `403 Forbidden` from the backend/security layer instead of `301`.
- `POST https://api.winworldteam.com/api/v1/payments/stripe` now returns backend JSON `500` for missing `Stripe-Signature` instead of `301`.

The Stripe result confirms the request reaches the Spring endpoint. The `500` is just because the test payload lacks Stripe's required signature header, though the backend should eventually map this to a cleaner `400`.

The CryptoCloud `403` was produced by an intentionally empty `{}` body, so it proves the redirect is gone but does not yet prove a real CryptoCloud payload succeeds. Next isolation test should use a realistic fake payload with bogus token; expected result should be a backend validation/signature error, not a generic security `403`.

Follow-up fake payload tests:

- Form-urlencoded fake CryptoCloud payload with `token=bogus` reached backend and returned JSON `500` with `Invalid compact JWT string`.
- JSON fake CryptoCloud payload with `token=bogus` reached backend and returned the same JSON `500`.
- Fake Stripe payload with bogus `Stripe-Signature` reached backend and returned JSON `400 Security error`.

Interpretation:

- Caddy routing is fixed: both `api.winworldteam.com` payment endpoints now hit Spring.
- Stripe path and signature verification are reachable.
- CryptoCloud path supports both form and JSON at routing/controller level.
- A concrete backend bug remained: malformed CryptoCloud JWT tokens escaped as generic `500` because `CryptoPaymentService` only caught expired/signature exceptions, not malformed/general JWT parsing exceptions.

Real failed CryptoCloud callback evidence:

- Callback URL: `https://api.winworldteam.com/api/v1/payments/crypto`
- Provider says it sent `POST` with `User-Agent: Cryptocloud POSTBACK v2`.
- Body contained successful payment for order `788`, invoice `6H7AY3XU`, status `success`.
- Backend response was JSON `400` with message `Request method 'GET' is not supported`.

This is the strongest root-cause evidence for CryptoCloud. The provider sent POST to `api.winworldteam.com`, the old Caddy config returned a permanent redirect, and the provider/client followed the redirect as GET. Spring then correctly rejected `GET /api/v1/payments/crypto` because the controller only supports POST. The payload itself looked structurally valid: JWT token id matched invoice id `6H7AY3XU`, and its `exp` value (`1775811228`) corresponds to 2026-04-10 08:53:48 UTC, about five minutes after the observed failed backend response at 2026-04-10 08:48:49 UTC.

Because CryptoCloud documents that postback JWTs are generated for each notification and valid for five minutes, replaying the old captured payload later will fail as expired. Recovery should use a fresh provider resend after the Caddy fix, or manual reconciliation after verifying the invoice in CryptoCloud.

Local patch added after these tests:

- Catch general `JwtException` / `IllegalArgumentException` in CryptoCloud token validation.
- Convert CryptoCloud token validation failures to the same custom `SecurityException` class used by Stripe signature failures.
- Add `CryptoPaymentServiceTest` covering malformed token and valid signed token paths.

Verification:

```powershell
.\gradlew.bat test --tests "com.winworld.coursestools.service.payment.impl.*PaymentServiceTest"
.\gradlew.bat test
```

Real CryptoCloud failure evidence:

- Invoice `6H7AY3XU`, order `788`, status `success`, callback URL `https://api.winworldteam.com/api/v1/payments/crypto`.
- CryptoCloud recorded the outbound request as `POST`.
- Backend/provider response body was `400 Http method error` with message `Request method 'GET' is not supported` at `2026-04-10T08:48:49.397419374`.
- This exact POST-to-GET mismatch is consistent with the pre-fix Caddy `301` redirect from `api.winworldteam.com` to `api.coursestools.com`. Many clients follow `301/302` by issuing a `GET`, which drops the webhook body.
- The token expiry claim for this payload was shortly after the failed response, so it was not expired at the time of the observed failure. The failure happened before token validation could matter.

Conclusion for this CryptoCloud invoice: the root cause is effectively proven as ingress redirect behavior, not payment business logic. Recovery for old invoices should not rely on replaying the same expired token unless CryptoCloud generates a fresh signed postback; use provider/API verification plus manual/idempotent order processing if the token is expired.

Recommended Caddy hardening:

```caddyfile
coursestools.com {
    handle /api/v1/payments/* {
        reverse_proxy backend:8080
    }

    # existing plausible, metrics, and frontend handlers...
}

api.winworldteam.com {
    handle /api/v1/payments/* {
        reverse_proxy backend:8080
    }

    redir https://api.coursestools.com{uri} permanent
}
```

After reloading Caddy, use one-line PowerShell tests from Windows:

```powershell
curl.exe -i -X POST "https://api.coursestools.com/api/v1/payments/stripe" -H "Content-Type: application/json" --data "{}"
curl.exe -i -X POST "https://api.coursestools.com/api/v1/payments/crypto" -H "Content-Type: application/json" --data "{}"
curl.exe -i -X POST "https://coursestools.com/api/v1/payments/stripe" -H "Content-Type: application/json" --data "{}"
curl.exe -i -X POST "https://coursestools.com/api/v1/payments/crypto" -H "Content-Type: application/json" --data "{}"
```

Expected result after routing is fixed: controlled backend errors such as invalid signature or validation failure, not Next.js HTML `404`.

## Source notes

Official Stripe docs used for the patch rationale:

- Stripe webhook handler examples acknowledge unhandled event types while still returning success: https://docs.stripe.com/webhooks/configure
- Stripe says webhook endpoints should quickly return a successful 2xx before complex logic: https://docs.stripe.com/webhooks
- Stripe Workbench event deliveries show delivery status, HTTP codes, and retry timing; live mode retries for up to 3 days: https://docs.stripe.com/workbench/webhooks
- CryptoCloud POSTBACK v2 supports JSON and form-urlencoded formats, and signs notifications with an HS256 JWT valid for five minutes: https://docs.cryptocloud.plus/en/api-reference-v2/postback
