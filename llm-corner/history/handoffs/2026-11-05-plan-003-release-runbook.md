# 2026-11-05 — PLAN-003 built; release runbook (operator-run)

Author: Claude Code (Opus 5.5)
Commits: pushed to feature branches, PRs open, **nothing merged**:

| Repo | PR | Merge order | Gate |
|---|---|---|---|
| alert-bot-v2 | CoursesTools/alert-bot-v2#2 | 1 | step 0 check is clean |
| alert-router | CoursesTools/alert-router#3 | 2 | host env from step 2 is in place, or every TradingView webhook 404s |
| backend-v2 | CoursesTools/backend-v2#41 | 3 | bot #2 deployed (else grace users keep getting alerts) |
| admin-web | CoursesTools/admin-web#16 | 4 | backend #41 deployed |

## State of the world

- Everything the operator decided on 11-05 is built and verified locally:
  - grace pauses alerts (DEC-006), grace length in admin-web;
  - ops alerts to Telegram with history, search and per-category switches
    (DEC-007);
  - router secret URL and bot token;
  - revert of the 4 unpaid renewals (V18).
- None of it is deployed. Merging a PR to master deploys that service.
- Remote host writes are blocked for agents, so every host change is a
  script in `C:/Users/taras/Desktop/ct-projects/ops-scripts/` that the
  operator runs. Secrets are in `ops-scripts/secrets/`: never printed or
  committed, but a plain NTFS folder (no Unix modes). Keep it out of every
  repo and move the values to the password manager.
- The review before pushing found a real bypass in the bot's token filter:
  `/api/alert;x=1` and `/api/%61lert` skipped the check. It was fixed and
  tested (`6116d4e`) before the PR.

## Runbook

Run these in order, from `ct-projects/ops-scripts/` in Git Bash.
`ct-backend` asks for its password.

0. **Read-only sanity check of the bot filter:**
   `sh 2026-11-05-check-bot-filter.sh`.
   - The "muted" rows must hold only `GRACE_PERIOD`/`TERMINATED` or
     `no subscription`. Any other status, or an SQL error, means stop.
   - The muted counts show how many users stop receiving alerts once the
     bot deploys: leftover cancelled customers, plus users currently in
     grace.
1. **Telegram relay through Amsterdam:**
   - `ssh ct-backend 'sh -s' < 2026-11-05-amsterdam-telegram-relay.sh`;
   - then `ssh ct-alert-vps 'sh -s' < 2026-11-05-alert-bot-use-relay.sh`.

   The admin-web deploy only restarts Caddy, which re-reads the patched file,
   so the relay survives deploys.
2. **Alert host env:** `sh 2026-11-05-alert-host-env.sh`.
   - It writes the token and secret, and `ALERT_ROUTER_ALLOW_LEGACY_PATH=true`.
   - It restarts nothing.
   - It saves the new TradingView URL to
     `secrets/tradingview_webhook_url.txt`.
3. **Backend env:** `sh 2026-11-05-backend-env.sh`. It sets `ALERT_BOT_URL` to
   `https://alerts.tw1.su/api/alert/message` and adds the token. It restarts
   nothing.
4. **Merge alert-bot-v2 #2.** After the Action finishes:
   - confirm the container is new: `docker ps` shows a fresh "Up" time. The
     router deploy once silently kept the old image;
   - check `docker logs ct-alert-bot --since 5m`;
   - check stats from your PC:
     ``curl -H "X-Internal-Token: $(cat secrets/alert_bot_internal_token.txt)" https://alerts.tw1.su/api/ops/stats``.
5. **Merge alert-router #3.** Confirm the container is new. When the next
   TradingView alert fires, it should arrive in Telegram as usual. The router
   log shows `legacy unauthenticated URL` warnings until step 8.
6. **Merge backend-v2 #41.**
   - Flyway applies V16–V18 on startup.
   - Within a minute, Telegram (`@ct_maintenance_bot` → 639072017) gets
     "Backend started". You must have pressed Start in that bot once.
   - No `alerts.monitoring-not-configured` alert should appear. If it does,
     step 3 did not apply.
7. **Merge admin-web #16.**
   - Open Settings and press "Send test message".
   - Open System Alerts and check that "Backend started" is there.
8. **Update the 2 TradingView watchlist alerts** to the URL in
   `secrets/tradingview_webhook_url.txt` (same host, new path). Wait for
   one alert to arrive.
9. **Close the old paths:**
   `ssh ct-alert-vps 'SINCE=30m sh -s' < 2026-11-05-alert-security-flips.sh`
   (`SINCE` is the time since step 8).
   - It refuses if any legacy or unauthenticated call is still seen.
   - Afterwards it shows the legacy URL → 404 and the bot without a token →
     401.
   - Checked 11-05: there are no active Caddy health checks for
     `alerts.tw1.su` and no compose healthchecks for the bot or router, so a
     deny-by-default bot cannot be marked unhealthy.
10. **Manual follow-ups:**
    - cancel TradingView access for users 3074, 5402, 247, 2541, 96 (V16/V18
      reverted them; the next hourly run moves them to grace and emails
      them);
    - delete `backend-v2/tmp_ops_tg_bot_token.txt`;
    - move `ops-scripts/secrets/*` into your password manager.

## Open / deferred

- frontend-v2 still hardcodes 7 grace days. Task written to
  `frontend-v2/claude-tasks/GRACE-PERIOD-FROM-API.md` (untracked there).
  Don't change grace in admin-web until it ships.
- psentogo (user 5561): still unresolved. The bot log screenshot does not
  cover 07:16 MSK. Ask the TV-bot owner for that window.
- Backlog additions: Hibernate JVM-zone binding (Low), read-time discard
  leaves inert alert rows (Low).

## Landmines for the next session

- Router merge before step 2 → every TradingView webhook 404s (default
  `ALLOW_LEGACY_PATH=false`).
- Caddy access logs on `ct-alert-vps` may record the secret webhook path.
  The host is root-only; rotate by adding a second secret
  (comma-separated), updating TradingView, then removing the old one.
- `alerts.no-incoming` goes CRITICAL after 30 minutes without a
  TradingView alert (reminders every 6h). Watch the first weekend. If quiet
  market hours trigger it, switch off the Alert delivery category in
  Settings until a quiet-hours rule exists.
- Shortening grace in admin-web terminates every subscription already past
  the new cutoff on the next hourly run, and deletes their alert lists.
- A local backend run on a non-UTC machine shows shifted times for
  JPA-read timestamps. Start it with `-Duser.timezone=UTC`
  (`docs/architecture/persistence-scheduling-events.md`).

## Verification record

- backend-v2: `./gradlew build`, 156 tests, 0 failures.
  - Booted locally on a fresh PG16 with V1–V18.
  - E2E API pass: 403 for PARTNER/anonymous; 400 validation; 409 for stale
    version, duplicate recipient and test cooldown; 404 for an unknown
    alert; LIKE escaping, keyset paging, filters.
  - Browser pass of both admin pages. It found a list-vs-single timestamp
    mismatch, which is fixed (single read now uses the same UTC mapper).
- alert-bot-v2: `./gradlew build`, 25 tests, including encoded and matrix
  path bypass tests (they failed before the fix with 200 OK).
- alert-router: `./gradlew build` green, unchanged since the earlier review.
- admin-web: `bun run build` green.
- Ops scripts: `sh -n` syntax check of each script and of the generated
  remote halves (heredocs). **Not executed**, because they touch prod
  hosts.
- Not verified: the bot filter against real prod data (step 0 does it);
  real Telegram delivery from the backend (no token locally); RDAP, TLS and
  Loki checks against the real internet (they ran with
  `OPS_CHECKS_ENABLED=false` locally, and unit tests cover the evaluation
  logic only).
