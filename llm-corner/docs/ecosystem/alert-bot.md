# Alert bot (alert-bot-v2) and alert router

Kotlin/WebFlux Telegram bot that delivers TradingView alert messages to
subscribed users. **Alerts are the core product.** The backend calls exactly
one endpoint on it: a subscription confirmation.

## Where it lives

| What | Where |
|---|---|
| Repos on operator's laptop | `C:/Users/taras/Desktop/ct-projects/alert-bot-v2`, `.../alert-router` (Kotlin, Spring WebFlux, R2DBC) |
| Prod host | `ct-alert-vps` = 37.77.105.213 (Saint Petersburg, Timeweb; ssh key auth, alias in `~/.ssh/config`). Containers `ct-alert-bot`, `alert-router`, also `helper-tg-bot`, `wcsmc_backend`, `harmonic_backend`, `metatrader_backend`, `caddy`, `mongodb`. Verified 2026-11-05. |
| Public URLs (host Caddy) | `alerts.tw1.su` → `ct-alert-bot:8080`; `winworldteam.ru` → `alert-router:8080` |
| Router → bots | `COURSESTOOLS_BOT_URL=http://ct-alert-bot:8080/api` (WCSMC, SMCTB); `WCSMC_BOT_URL`, `HARMONIC_BOT_URL` are other products' bots |
| Backend's `ALERT_BOT_URL` | `https://alerts.tw1.su/api/alert/message` (prod `.env` since 2026-11-05). Before, it was `http://193.160.209.73:8080/…`, a host referenced nowhere else and unreachable from both prod hosts, so confirmations were never delivered. The Loki host is 77.232.135.132, not this one. |
| Backend → bot auth | `X-Internal-Token: ${ALERT_BOT_INTERNAL_TOKEN}` on the confirmation call and on `GET /api/ops/stats` (ops checks, `../architecture/ops-alerts.md`) |
| Deploy | push to `master` in each repo → GHCR image `:latest` → ssh `compose pull` + `up` (`.github/workflows/docker-build.yml`). Compose file and `.env` live only on the host. |
| Logs | `docker logs ct-alert-bot` / `alert-router` (JSON). Not shipped to Loki. |

## Delivery flow

TradingView → `alert-router` `POST /api/alerts/{secret}` (routes by
`indicator`) → `ct-alert-bot` `POST /api/alert` → the bot looks up the
`alerts` row. It then reads subscribers from `users_alerts` ⨝ `user_socials`
**directly in the backend's PostgreSQL** (R2DBC), keeping only users with a
COURSESTOOLS subscription in `GRANTED` or `PENDING` → per-chat buffer → the
scheduler sends to Telegram every minute.

- Subscriptions are read live from the shared DB; the bot keeps no cache.
  - A status other than GRANTED/PENDING (grace, terminated) **pauses**
    delivery.
  - Deleting `users_alerts` rows **ends** it (C18, DEC-006).
- Router webhook auth: the secret path segment must be one of
  `ALERT_ROUTER_WEBHOOK_SECRETS` (comma-separated, constant-time compare);
  an unknown secret returns 404. The legacy `POST /api/alerts` works only
  while `ALERT_ROUTER_ALLOW_LEGACY_PATH=true`. The 2 TradingView watchlist
  alerts carry the secret URL.
- Bot auth: `X-Internal-Token` must match one of `ALERT_BOT_INTERNAL_TOKENS`
  (comma-separated, for rotation).
  - `GET /api/ops/stats` always requires it.
  - Once `ALERT_BOT_REQUIRE_TOKEN=true`, every path requires it (deny by
    default). Until then, unauthenticated calls are accepted, and those to
    `POST /api/alert` and `/api/alert/message` are WARN-logged at most once a
    minute.
  - The filter matches the path the way Spring routes it (decoded,
    `;params` stripped), so encoded spellings cannot bypass it.
  - The router sends its token only to the CoursesTools bot.
- `GET /api/ops/stats` returns counters for the last 15 min and 24h
  (`alertsReceived`, `messagesSent`, `sendAttemptsFailed`, `chatsRequeued`,
  `alertsDroppedStale`, `permanentRejections`), plus `queueSize`,
  `lastAlertReceivedAt`, `lastMessageSentAt` and `startedAt`.
- "Alert not found" warnings are mostly expected: the router sends every WCSMC
  alert to both the CoursesTools bot and the WCSMC bot, and CoursesTools only
  sells a subset of events. Possible exceptions: `Crossing_equlibrium` arrives
  tagged WCSMC while V8 moved it to `smctb`, and `EQH`/`EQL` arrive tagged
  SMCTB with no `smctb` rows. Unverified, see backlog.

## Telegram reachability (binding constraint)

The host is in Russia; the ISP blackholes connections to Telegram. Measured
2026-11-05 from `ct-alert-vps`:

- `api.telegram.org`'s current public IP `149.154.166.110` is fully blocked,
  and so is IPv6.
- Only `149.154.167.32` / `.132` / `.220` answer as the Bot API, at **55–85%
  per attempt**.
- The bot pins `.220` via compose `extra_hosts`.

The bot (branch `fix/reliable-telegram-delivery`) therefore:

- uses a 5s connect timeout (the library default was 75s per failed send,
  stalling every user's alerts);
- retries network errors, 429 (honouring `retry_after`) and 5xx with backoff;
- requeues undelivered alerts for the next cycle and stops a cycle when
  Telegram is down;
- supports `TELEGRAM_API_BASE_URL` and `TELEGRAM_PROXY_*` so Telegram traffic
  can leave via a non-RU relay with a config change only.

The durable fix is a relay through Amsterdam, which reaches Telegram 20/20.
The prod Caddy on `ct-backend` serves `https://api.coursestools.com/tg-relay/*`
→ `https://api.telegram.org/*`. Only 37.77.105.213 is allowed (all other
callers are aborted), and access logs are skipped so bot tokens in the path
never hit disk. The bot uses it with
`TELEGRAM_API_BASE_URL=https://api.coursestools.com/tg-relay/bot` in
`/root/AlertRouter/.ct-alert-bot.env`. Live since 2026-11-05 13:06 UTC
(scripts: `ct-projects/ops-scripts/2026-11-05-amsterdam-telegram-relay.sh`,
`2026-11-05-alert-bot-use-relay.sh`). The Caddyfile on `ct-backend` is
not in any repo. The admin-web deploy only restarts Caddy, which keeps the
block; a Caddyfile rewrite must keep `handle_path /tg-relay/*`, or every
alert stops.

## What we consume

- `POST /api/alert/message?telegramId={id}` (no body) sends the user a fixed
  "✅ You have successfully subscribed for new alerts!" message. It does
  nothing else: no DB access, and Telegram errors are swallowed.
  - Our caller: `listener/UserAlertChangeListener.java:28-36`,
    `@TransactionalEventListener @Async` on `UserAlertsChangeEvent`. It skips
    a blank `telegramId`.
  - Published **only** by `AlertService.subscribeOnAlerts`. Unsubscribe,
    unsubscribe-all, the expiry job and Telegram unlink must not publish it:
    they would show "subscribed" to a user whose alerts were just removed,
    and a missing id is sent as `?telegramId` with no value, which the bot
    rejects with 400.
- Their handler: `alert-bot-v2/.../controller/AlertController.kt`
  (`@RequestParam telegramId: String`, required).

## Binding invariants on our side

- The bot reads `alerts`, `users_alerts`, `user_socials` directly
  (`alert-bot-v2/.../repository/AlertRepository.kt`). Schema changes to these
  tables are cross-project contract changes.
- Fire-and-forget confirmation: no `@Retry`. A bot outage loses only the
  confirmation, never the DB change.
- The bot's delivery filter reads `users_subscriptions.status`,
  `subscription_plans` and `subscription_types.name = 'COURSESTOOLS'`.
  Renaming those columns or values, or adding a paid status, is a
  cross-project contract change (C18).
- Both public hostnames are reachable from the internet through the alert
  host's Caddy. Authentication is enforced since 2026-11-05 14:05 UTC:
  - `ALERT_ROUTER_ALLOW_LEGACY_PATH=false`: the old URL and wrong secrets
    get 404;
  - `ALERT_BOT_REQUIRE_TOKEN=true`: any path without the token gets 401,
    including encoded and `;param` spellings (verified in prod).

  Rotate a secret or token by adding a second comma-separated value,
  switching callers, then removing the old one.
- Restart order matters. The router retries a refused connection for only
  about 0.7s (3× backoff from 100 ms), which is shorter than a bot restart, so
  alerts that arrive meanwhile are lost (11 during the 14:05 flip). Restart the bot first and the router
  after it, and not in the first seconds after a 5-minute mark, when bar
  closes fire bursts of alerts.
- Backend alert domain docs: `../architecture/partnership-referrals-alerts.md`.

## Sending work to their agents

Their inbox path: none established (no `claude-msgs/` in `alert-bot-v2`).
TODO(operator): create one on first cross-project message; until then follow
`../../protocols/messaging.md` and drop the message in their repo root.

## Change history

- 2026-07-12: file created from verified code (both repos read).
- 2026-11-05: corrected the bot's behaviour (it does not re-sync
  subscriptions), the real prod host, Telegram reachability, delivery fixes,
  and the confirmation-only contract.
- 2026-11-05: grace pause filter, internal token, stats endpoint, router
  secret path, Amsterdam relay (PLAN-003, DEC-006).
