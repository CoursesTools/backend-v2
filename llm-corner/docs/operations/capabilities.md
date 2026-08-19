# Capabilities — You Have These, Use Them

What an agent MAY drive directly — and the boundaries. Agents drive
prod observability, CI status, and local builds directly from the
operator's machine: **do not ask the operator to run commands you can
run yourself.** The one hard wall: this repo's `master` auto-deploys a
live payment backend, so everything that mutates prod is gated.

## Direct-drive

| Tool | Use | Notes |
|---|---|---|
| Read/Grep/Glob + `./gradlew build` / `test` | code + local verification | Full test suite runs locally; no prod involved. |
| Loki HTTP API | **query prod backend logs, no SSH needed** | `http://77.232.135.132:3100` — requires header `X-Scope-OrgID: backend` (without it: `no org id`). Prod backend logs stream as `{container_name="backend"}` (compose_project `coursestools`). Also reachable as `https://loki.winworldteam.com`. Verified live 2026-07-12. |
| Grafana | dashboards / ad-hoc LogQL | `http://77.232.135.132:3000` (health endpoint answers unauthenticated; UI login may be needed for dashboards). |
| `ssh ct-backend` | backend VPS 5.129.216.95 (root) | Read logs, `docker ps`, tail files, run probes. **Password auth** — `VPS_BACKEND_PASSWORD` in `backend-v2/.env:52`. `sshpass`/`plink` are NOT installed on this Windows box, so plain `ssh` prompts interactively; prefer the Loki API for logs and involve the operator for host-level sessions. Mutations → ask first. |
| `ssh ct-logs` | observability VPS 77.232.135.132 | Same password-auth caveat (`VPS_LOGS_PASSWORD`, `.env:56`). Grafana/Loki/Prometheus live here. |
| `gh` | GitHub CLI (repo `CoursesTools/backend-v2`) | Authenticated with `repo`+`workflow` scopes. List runs, view CI logs, fetch issues, inspect workflows. PR creation → ask first. **Exception:** `gh workflow run docker-build.yml` is a prod deploy, not a read — see gated list. Don't open browser tabs for things `gh` can do. |
| `gh run list` / `gh run watch <id>` | watch CI | Non-mutating. The only workflow is `.github/workflows/docker-build.yml` (build → push image → deploy). |
| `docker compose up -d` (repo root, **local**) | local dev stack | Local-only compose: postgres on host port 5433, redis, backend on 8080/8081 (`docker-compose.yml:1-49`). Never confuse with the prod compose in `/root/coursestools` on ct-backend. |

There is no staging environment: local dev stack or prod, nothing in
between. Details of hosts, containers, and prod paths: `servers.md`.
Log query recipes: `log-playbook.md`.

### Loki quick form (the default way to read prod logs)

```sh
curl -s -G -H "X-Scope-OrgID: backend" \
  "http://77.232.135.132:3100/loki/api/v1/query_range" \
  --data-urlencode 'query={container_name="backend"} |= "ERROR"' \
  --data-urlencode 'limit=50'
```

Labels available: `compose_project`, `compose_service`,
`container_name`, `filename`, `host`, `level`, `logger`,
`service_name`, `source`, `thread` — so `{container_name="backend",
level="ERROR"}` and `logger=` filters work server-side.

## Things that REQUIRE operator approval before acting

- **Any `git push` to `master`.** `docker-build.yml` triggers on every
  master push (`.github/workflows/docker-build.yml:3-5`) and its
  `deploy` job SSHes to the server and rolls the `backend` container
  (`docker-build.yml:56-72`). A push IS a prod deploy of the live
  Stripe payment backend — there is no staging gate.
- **`gh workflow run docker-build.yml`** — the workflow also has a
  `workflow_dispatch` trigger (`docker-build.yml:6`), so dispatching it
  rebuilds and redeploys prod exactly like a master push.
- Any `gh pr merge` / `gh pr close`.
- **Restarting / recreating prod containers** on ct-backend
  (`docker compose restart|up|down` in `/root/coursestools`), even
  "just the backend" — it drops live sessions and in-flight webhooks.
- **Prod DB writes** — read freely while debugging (SELECT via
  `docker exec` psql on ct-backend); mutate only with the operator's
  explicit go-ahead unless the active task is exactly that.
- DB migrations on prod. Flyway migrations live in
  `src/main/resources/db/migration/` (currently V1–V14) and
  apply automatically on backend startup after a deploy — which is one
  more reason a master push is gated.
- **Anything touching Stripe live credentials or Stripe state.**
  `STRIPE_SECRET` is a live key (`sk_live_…`, `backend-v2/.env:39`);
  Stripe-side mutations (cancel/refund/webhook endpoint edits) affect
  real customer money.
- **Mutating calls to the external bots.** TV access bot
  `http://45.141.184.24:4320` (`/open`, `/username_changer`,
  `/withdrawal`) grants/renames/revokes real TradingView access; alert
  bot `http://193.160.209.73:8080/api/alert/message` messages real
  users (URLs: `.env:23-27`). Probe reachability only.
- **The co-located foreign stacks on ct-backend**: `plausible` and
  `pixel-canvas` (plus `caddy` and `frontend`, which are not this
  repo's) share the prod docker-compose. Never restart, reconfigure,
  or `docker image prune` around them (hard invariant — see
  `../conventions/mindset.md`).
- Credential rotation / secrets editing (`.env` here, GitHub Actions
  secrets `SSH_HOST`/`SSH_USER`/`SSH_PRIVATE_KEY`/`GHCP_PAT`, server
  env in `/root/coursestools`).

## Things forbidden outright

- `--force` push to any shared branch.
- `--no-verify` on commits unless the operator explicitly asks.
- Editing applied Flyway migrations — checksum mismatch bricks startup
  on the next deploy; always add a new `V<n+1>__*.sql`.
- Committing secrets. `.env` is git-ignored (`.gitignore:5`, `*.env`)
  and must stay untracked; never paste its values into code, docs, or
  commit messages.
