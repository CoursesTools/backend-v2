# TV access bot

External bot that grants/renames paid users' access to the premium
TradingView indicators. The backend only ever POSTs commands to it —
fire-and-forget; there is no query or revoke API.

## Where it lives

| What | Where |
|---|---|
| Repo on operator's laptop | TODO(operator): not in `ct-projects/`; add path or note "no repo access" |
| Prod deploy | `45.141.184.24:4320` (standalone host; likely Moscow-time infrastructure — see timezone invariant below) |
| Runtime endpoints (from inside this app) | `http://45.141.184.24:4320/open`, `/username_changer`, `/withdrawal` |
| Its docs workspace | TODO(operator): unknown |

URLs come from `.env` → `application.yml`:
`ACTIVATING_BOT_URL` → `urls.activating-bot`, `CHANGE_TRADINGVIEW_BOT_URL` →
`urls.change-tradingview-bot`, `WITHDRAWAL_URL` → `urls.withdrawal`
(`.env:25-27`, `src/main/resources/application.yml:81-83`).

## What we consume

### `POST /open` — grant/extend indicator access

Body: `ActivateTradingViewAccessDto`
(`src/main/java/com/winworld/coursestools/dto/external/ActivateTradingViewAccessDto.java:18-44`):

```json
{ "email": "...", "tier": "PRO|ESSENTIALS", "tv": "<tradingview nickname>",
  "expiration": "2026-08-12T21:00:00", "isLifetime": false }
```

- `expiration` is a **naive** ISO local date-time (no offset), serialized by
  the app-wide ObjectMapper; deliberately no strict `@JsonFormat` (DTO
  comment, lines 37-42).
- Successful customer payments use `customerPaymentGrant(...)`, which pads
  non-lifetime expiry by one day. Trials, admin actions, lifecycle syncs, and
  Direct Extend use `exactGrant(...)` with no pad (DEC-002).
- Lifetime subs send far-future expiry plus `isLifetime: true`.

Called from `service/external/ActivatingSubscriptionService.java:30` — call
sites: `listener/SubscriptionChangeStatusListener.java:64` (post-commit async)
and `service/SubscriptionService.java:393,414,622`.

### `POST /username_changer` — rename a user's TradingView nickname

Body: `ChangeTradingViewNameDto`
(`dto/external/ChangeTradingViewNameDto.java:88-97`): `old`, `new`, `tier`,
`expiration` (exact DB expiration via `rename(...)`), `isLifetime`.
Called from `service/user/UserSocialService.java:99` when a user changes
their TradingView name.

### `POST /withdrawal` — partner cashback payout request

Body: `WithdrawRequestDto` (`dto/transaction/WithdrawRequestDto.java:9-16`):
`email`, `wallet`, `amount` (decimal **USD**, converted from internal cents),
`transactionId`, `currency` (always `USD`, `mapper/TransactionMapper.java:19`).
Called from `service/user/UserTransactionService.java:105`
(`POST /api/v1/transactions/withdraw` flow); the bot's string response body is
only logged (`UserTransactionService.java:92-94`).

## Failure handling on our side

- **404 from the bot** = TradingView user does not exist → permanent
  `TradingViewUserNotFoundException`, never retried
  (`ActivatingSubscriptionService.java:33-37,56-58` + specific fallback
  overloads at lines 97-107).
- **Any other failure** → resilience4j `@Retry(name = "default")` (3 attempts,
  exponential backoff, `application.yml:60-70`), then a durable outbox row in
  `trading_view_retry_jobs` (`TradingViewRetryService.enqueue`,
  `service/external/TradingViewRetryService.java:55-78`), drained by
  `TradingViewRetryScheduler` with backoff `60s…86400s`, max 10 attempts,
  batch 20 (`application.yml:105-109`). Admin UI:
  `/api/v1/admin/tv-retry/*` (`controller/AdminController.java:115-138`).
- Withdrawal failures throw `ExternalServiceException` to the user after
  retries (`UserTransactionService.java:114-121`); no durable queue.

## Binding invariants on our side

- **The bot never answers queries.** All three endpoints are write-only
  commands; the backend cannot confirm a grant landed. Success = 2xx, nothing
  more (verified: only `postForEntity` calls exist against these URLs).
- **No revoke channel.** Expired access is only cut off by the expiration
  timestamp the bot already holds. Payment grants keep a one-day safety pad;
  non-payment commands intentionally replace it with their exact selected
  expiration.
- **Naive timestamps + likely Moscow tz on the bot side** — never change the
  wire format without bot-owner coordination. The one-day safety pad remains
  only for real customer payments.
- Final exact-or-buffered expiration is persisted into retry payload JSON, so
  replays do not transform or compound it.

## Sending work to their agents

TODO(operator): no known repo/inbox for the bot. Coordinate changes with the
bot's maintainer manually; record the contact/channel here.

## Change history

- 2026-07-12 — file created from verified code.
