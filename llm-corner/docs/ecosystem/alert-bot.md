# Alert bot (alert-bot-v2)

Kotlin/WebFlux Telegram bot that delivers TradingView alert messages to
subscribed users. The backend calls exactly one endpoint on it: a
"your alert subscriptions changed" notification.

## Where it lives

| What | Where |
|---|---|
| Repo on operator's laptop | `C:/Users/taras/Desktop/ct-projects/alert-bot-v2` (Kotlin, Spring WebFlux, R2DBC) |
| Prod deploy | `193.160.209.73:8080` (standalone host) |
| Runtime endpoint (from inside this app) | `http://193.160.209.73:8080/api/alert/message` |
| Its docs workspace | none found (repo has only `.claude/`); TODO(operator): confirm |

URL comes from `.env:23` `ALERT_BOT_URL` → `urls.alert-bot`
(`src/main/resources/application.yml:85`).

## What we consume

- `POST /api/alert/message?telegramId={id}` (empty body) — tells the bot to
  send the user a Telegram message about their changed alert subscription.
  - Our caller: `listener/UserAlertChangeListener.java:44-57` —
    `@TransactionalEventListener @Async` on `UserAlertsChangeEvent`, so it
    fires after commit, off the request thread.
  - Event published by `service/AlertService.java:146,164,172` on alert
    subscribe / unsubscribe / unsubscribe-all.
- Their side of the contract (verified in their repo):
  `alert-bot-v2/src/main/kotlin/com/winworld/alertbot/controller/AlertController.kt:36-39`
  (`@RequestMapping("/alert")` + `@PostMapping("/message")`), served under
  WebFlux `base-path: /api` (`alert-bot-v2/src/main/resources/application.yml:5`).

## What we do NOT consume

- The bot's main ingest `POST /api/alert` (TradingView alert payloads) is fed
  by the separate `alert-router` project, not by this backend. The routing
  contract belongs to the alert-router project (indicators `WCSMC`, `SMCTB`,
  `HP` routed per bot). Do not conflate the two projects.
- The alert bot reads the backend's PostgreSQL schema directly: its R2DBC
  queries hit `alerts`, `users_alerts`, `user_socials`
  (`alert-bot-v2/.../repository/AlertRepository.kt:12-46`). Schema changes to
  these tables are cross-project contract changes.

## Binding invariants on our side

- `telegramId` is the user's Telegram id from `users_socials`; users without
  a bound Telegram id would send `telegramId=null` as a literal query param —
  callers publish the event only in Telegram-bound alert flows.
- Fire-and-forget: no `@Retry`, no fallback on this call. A bot outage only
  loses the notification, never the DB change (the listener runs post-commit).
- Backend alert domain docs: `../architecture/partnership-referrals-alerts.md`.

## Sending work to their agents

Their inbox path: none established (no `claude-msgs/` in `alert-bot-v2`).
TODO(operator): create one on first cross-project message; until then follow
`../../protocols/messaging.md` and drop the message in their repo root.

## Change history

- 2026-07-12 — file created from verified code (both repos read).
