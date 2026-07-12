# Fix: CryptoCloud webhook reliability

## Commit message

```
fix: harden CryptoCloud webhook + invoice creation

Accept JSON postbacks (V2 API may send either form or JSON), filter by
status=success, send required currency field, fix time_to_pay JSON name,
and add diagnostic logging across the entire crypto payment path.
```

## PR title

```
fix: CryptoCloud webhook reliability — accept JSON, filter status, add diagnostics
```

## PR description

### Why

Users paying via CryptoCloud were not getting access granted after a successful payment — backend never updated the order/subscription. There was no diagnostic logging on the crypto webhook path so the failure mode was invisible. Audit of the code against the CryptoCloud V2 API docs (https://docs.cryptocloud.plus/ru/api-reference-v2/) surfaced four concrete defects, each capable of causing the symptom on its own.

### Defects fixed

| # | Defect | File |
|---|---|---|
| 1 | `/v1/payments/crypto` only consumed `application/x-www-form-urlencoded`. CryptoCloud V2 sends postbacks as **either** form or JSON depending on dashboard config — JSON postbacks were getting `415` and dropped. | `controller/PaymentController.java` |
| 2 | Webhook DTO had no `status` field. We were processing every postback as success regardless of whether CryptoCloud reported `success`, `partial`, `fail`, etc. | `dto/payment/crypto/CryptoRetrieveDto.java` |
| 3 | `currency` was missing from invoice-create requests but is **required** by V2 API. | `dto/payment/crypto/CryptoInvoiceCreateDto.java` |
| 4 | `add_fields.timeToPay` was being serialized in camelCase. CryptoCloud expects `time_to_pay`, so the 24h timeout was silently ignored. | `dto/payment/crypto/CryptoInvoiceCreateDto.java` |
| 5 | No diagnostic logging on the crypto path → no way to see whether webhooks were arriving or where they were failing. | several |

### Changes

- **`PaymentController`** — second `@PostMapping("/crypto", consumes=APPLICATION_JSON_VALUE)` handler so Jackson binds JSON postbacks via `@RequestBody`. Both handlers log `orderId`, `invoiceId`, `status` on entry. Form-urlencoded handler unchanged in behavior.
- **`CryptoRetrieveDto`** — `@JsonProperty("invoice_id")` / `@JsonProperty("order_id")` (with `@JsonAlias` on camelCase forms) so Jackson can parse JSON. Manual `setInvoice_id` / `setOrder_id` setters retained for Spring form binding. New optional `status` field. Removed `@Setter(AccessLevel.NONE)` so Lombok generates standard setters that Jackson can use directly.
- **`CryptoInvoiceCreateDto`** — added required `currency` field. Added `@JsonProperty("time_to_pay")` on the nested map. Added `@JsonInclude(NON_NULL)` for cleaner payloads.
- **`CryptoPaymentService`** — sends `currency = "USD"` on create. Logs request and returned link. Throws `PaymentProcessingException` on empty CryptoCloud response instead of NPE. Filters postbacks: only proceeds when `status == "success"` (or absent — preserves backwards compat with bare form postbacks); non-success returns `null`. JWT signature error log now hints at the most likely root cause (`CRYPTO_SECRET` env var out of sync with project SECRET KEY in CryptoCloud dashboard). Fallback method log enriched with order/plan info.
- **`PaymentFacade`** — handles `null` from `cryptoPaymentService.processPayment` (non-success status) and logs at every step before forwarding to `OrderService`.

### Out of scope (operational, not code)

- Per-invoice `success_url` / `fail_url` are **not supported by CryptoCloud V2 invoice/create** — they're configured at the project level in the CryptoCloud dashboard. Same for the postback notification URL.
- **Action item:** in CryptoCloud dashboard → project settings → set postback **format = `json`** (more robust than form-data; status field is always present), and ensure notification URL points to `https://api.coursestools.com/api/v1/payments/crypto`.

### Frontend impact

None. `POST /v1/orders` request/response shapes are unchanged; the returned `paymentLink` is still the same CryptoCloud-hosted invoice URL.

### Test plan

- [ ] Set CryptoCloud dashboard postback format to `json`, notification URL to `…/api/v1/payments/crypto`
- [ ] Trigger a test payment via CryptoCloud
- [ ] Confirm logs show: `Received CryptoCloud postback (json) → Processing → validated → Forwarding to OrderService → Order ... processed successfully`
- [ ] Confirm `users_subscriptions` row created/updated and TradingView bot called with correct tier
- [ ] Test a partial/failed CryptoCloud payment (if possible) and confirm `Ignoring CryptoCloud postback ... non-success status`
- [ ] Verify Stripe payments still work (regression check — facade signature unchanged)
