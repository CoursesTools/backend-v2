# 2026-11-05 — PLAN-003 released to production

Author: Claude Code (Opus 5.5)
Commits: merged and deployed:
- alert-bot-v2#2
- alert-router#3
- backend-v2#41 (`e949d18`) and #42 (`506fc8f`)
- admin-web#16
- frontend-v2#48

Plus backend-v2#43 (V19, JWT filter, this record), merged on operator
instruction.

## State of the world

- **Grace pauses alerts** (DEC-006, C18). The alert bot delivers only to
  GRANTED/PENDING CoursesTools subscribers. 23 paying users are delivered,
  and 10 cancelled users (20,914 leftover subscriptions) are muted. Grace
  length is in admin-web Settings (default 7).
- **Ops alerts are live** (DEC-007, `docs/architecture/ops-alerts.md`).
  `@ct_maintenance_bot` sends to the owner (639072017), and the scheduled
  checks run. Prometheus `tasks_scheduled_execution_seconds_count{code_namespace=~".*OpsScheduler"}`
  proves they execute; healthy checks leave no other trace.
  System alerts so far:
  - #1 and #3: "Backend started";
  - #2: false Loki alert, fixed by #42;
  - #4: its "Resolved".
- **Alert security is enforced** (flips at 14:05 UTC). TradingView posts
  to the secret router URL, and the old URL gets 404. The bot requires
  `X-Internal-Token` on every path; encoded and `;param` bypasses get 401
  (verified in prod). Secrets: `ct-projects/ops-scripts/secrets/`.
- **Telegram relay** through Amsterdam is live. It lives in the
  `ct-backend` Caddyfile (not in any repo) and survives Caddy restarts.
- **Data fixes:** V16/V18 reverted subs 4999, 5165, 243, 5181, 95.
  - The 14:00 run moved 5165, 243 and 5181 to grace; 95 follows at 18:00
    UTC (end of its settlement window).
  - The operator cancelled TradingView access for users 3074, 5402, 247,
    2541 and 96. The temporary bot-token file was deleted, and the secrets
    were copied to the operator's password manager.
- Prod `/root/coursestools/.env` is now mode 600 (CI deploys as root; a
  deploy after the change succeeded).
- psentogo (user 5561): resolved by the operator. It was the TV access
  server not granting permissions.

## Verified in production

- Each deploy: a new image in the container, a clean startup, and V16–V18
  applied (`flyway_schema_history`).
- Bot filter (read-only SQL): no unexpected statuses. The first delivery to
  a paying user was at 13:18, and sends have had 0 failures since.
- Admin endpoints: 403 without, or with a bad, token. The public API
  returns 200.
- Code paths that had not run in prod yet:
  - TLS check: SNI works for all 6 hosts (43–79 days left);
  - RDAP: both sources return expiry (coursestools.com 2026-12-19,
    warning from about Nov 19);
  - the digest SQL ran read-only on prod data.
- Stripe webhooks: all 14 events of our 4 types in 3 days had
  `pending_webhooks=0`. The webhook probe gets the expected 400.

## Open / deferred

- backend-v2#43:
  - V19 moves `crossing_equlibrium` back to WCSMC. 63/63 arrival keys
    matched, and the migration was tested on PG16.
  - Unusable bearer tokens get 401 `"Invalid token"`.
  - Post-deploy verification is recorded in the changelog.
- New backlog items:
  - router retry on refused connection (11 alerts lost in the flip
    restart);
  - malformed-JWT log noise (fixed by #43).
- First-time scheduled runs to glance at:
  - daily infra check, 05:30 UTC;
  - digest, 09:00 MSK (06:00 UTC);
  - retention, 03:15 UTC.

## Landmines for the next session

- The router does not retry. A bot restart loses alerts that arrive during
  it. Restart the bot before the router, away from 5-minute marks.
- `ops_alert_state` is the only trace of check results; healthy runs log
  nothing. Use Prometheus (above) to prove the checks run.
- Agents may read and write prod hosts for this release, by operator grant
  in this session only. Merging to master (prod deploy) still needs the
  operator's explicit go-ahead per PR.
- `ct-backend` ssh is password auth. Drive it with `SSH_ASKPASS` reading
  `VPS_BACKEND_PASSWORD` from `.env`, and never print it.

## Addendum (same day, after #43)

- **Correction:** the router *does* retry connection errors and 5xx, 3× with 100 ms backoff (about
  0.7s). That window is just shorter than a bot restart, which is why the 11 alerts were lost. The
  landmine above stands; the backlog item now asks for a wider retry window.
- #43 verified in prod: V19 applied (668 rows wcsmc, 1,156 subscriptions kept). The first
  `Crossing_equlibrium` after deploy matched alert 18198 and was delivered. A junk bearer token gets
  401 `"Invalid token"` with 0 servlet errors.
- Agent docs refreshed in the other repos (all local, gitignored, no deploy):
  - `alert-bot-v2/claude-docs/CLAUDE_README.md` (new; excluded via `.git/info/exclude`);
  - `alert-router/claude-docs/CLAUDE_README.md` (rewritten);
  - `admin-web/claude-docs/CLAUDE_README.md` (new pages, backend message path, deploy, dev tips);
  - `frontend-v2/claude-docs/CLAUDE-DASHBOARD.md` (grace from the API), `CLAUDE_README.md` (backend
    pointers, deploy), `claude-git/GIT-grace-period-from-api-05-11-2026.md`.

  New ecosystem file: `docs/ecosystem/frontend-v2.md`.
