# Servers

Where prod runs + how to reach it. This is the FIRST file agents
grep when something breaks.

## Hosts

| Name | Purpose | ssh alias | Notes |
|---|---|---|---|
| 5.129.216.95 | PROD backend (app + DB + redis + caddy) | `ssh ct-backend` | user `root`, **password auth** (no pubkey). Host tz Europe/Moscow, but the `backend` container runs **UTC** — log timestamps and cron math are UTC. |
| 77.232.135.132 | Logs/observability | `ssh ct-logs` | user `root`, password auth. Grafana `:3000`, Loki `:3100`, Prometheus `:9090`. |

Both aliases are defined in `~/.ssh/config` (with `PreferredAuthentications
password`, `PubkeyAuthentication no`). **Credentials live in
`backend-v2/.env`**: `VPS_BACKEND_IP/USER/PASSWORD` and
`VPS_LOGS_IP/USER/PASSWORD` (`.env:50-56`). Never copy the values into
chat, docs, or command lines.

### Non-interactive SSH (agents)

`sshpass` is NOT installed locally. Password auth means plain `ssh ct-backend cmd`
blocks on a prompt. Use the `SSH_ASKPASS` technique: write a tiny askpass
script that greps the password out of `backend-v2/.env` (so the value never
appears in the command line or transcript), then:

```sh
SSH_ASKPASS=/path/to/askpass.sh SSH_ASKPASS_REQUIRE=force DISPLAY=:0 \
  ssh ct-backend '<command>'
```

Note: interactive/agent SSH into prod may be permission-gated — expect to ask
the operator before remote reads/writes on the box.

## File paths on the box (ct-backend)

```
/root/coursestools/                   # prod dir — compose project root
/root/coursestools/docker-compose.yml # prod compose (NOT the same file as the repo's dev compose)
/root/coursestools/.env               # prod secrets (source of truth for prod env)
docker logs backend                   # app logs (JSON in prod profile)
```

## Containers / services

```sh
ssh ct-backend 'docker ps --format "table {{.Names}}\t{{.Image}}\t{{.Ports}}"'
```

| Service | Container | Port | Notes |
|---|---|---|---|
| backend | `backend` | 8080 (API, context path `/api`), 8081 (actuator) | image `ghcr.io/coursestools/backend:latest` (docker-build.yml:51); ports per application.yml:44-47 + configs/actuator.yml:22 |
| PostgreSQL | external from current prod compose | from `POSTGRES_*` env | As verified 2026-08-19, prod `docker compose config --services` and `docker ps` contain no postgres service/container. Do not rely on the old `docker exec postgres` recipe. |
| redis | `redis` | 6379 (in-network) | `requirepass` from `REDIS_PASSWORD` (docker-compose.yml:24) |
| frontend | `frontend` | behind caddy | deployed separately — not from this repo's pipeline |
| caddy | `caddy` | 80/443 | reverse proxy + TLS for frontend/backend |
| plausible | `plausible` | — | analytics, shared service — do not touch |
| pixel-canvas | `pixel-canvas` | — | unrelated co-located app — do not touch |

Prod service list was rechecked read-only on 2026-08-19. Confirm again before
operations because shared services and database topology can change.

## Prod command form

Prod compose is a single default-named `docker-compose.yml` + `.env` in
`/root/coursestools` — no `-f`/`--env-file` flags needed, **but every compose
command must run from that directory** or compose targets nothing/creates a
new empty project:

```sh
ssh ct-backend 'cd /root/coursestools && docker compose ps'
ssh ct-backend 'cd /root/coursestools && docker compose logs --tail 200 backend'
ssh ct-backend 'cd /root/coursestools && docker compose pull backend && docker compose up -d backend'
```

Only ever `pull`/`up -d`/`restart` the **backend** service by name. Never
`docker compose down` the whole stack (it takes out caddy/frontend/plausible).

## Database access

Prod currently has no PostgreSQL container or psql client in the backend
image. Connection values remain in the backend container/server environment,
but must never be echoed into logs or transcripts. A secure operator-approved
read-only client/tunnel procedure is still needed; the old
`docker exec postgres psql ...` command fails with `No such container`.

Local dev: `docker compose up` in the repo gives postgres on host port
**5433** (docker-compose.yml:13).

## Deploy pipeline

**ANY push to `master` auto-redeploys prod.** Do not push to master unless
you intend to deploy.

Workflow: `.github/workflows/docker-build.yml` ("Build, Push and Deploy
Docker Image"), triggers: push to `master` or manual `workflow_dispatch`
(docker-build.yml:3-6).

1. `build-and-push` job: JDK 17 (temurin) → `./gradlew build` (tests run —
   a failing test blocks the deploy) → buildx builds and pushes
   `ghcr.io/coursestools/backend:latest` with a `:buildcache` registry cache
   (docker-build.yml:33-54).
2. `deploy` job (needs build): `appleboy/ssh-action` to
   `secrets.SSH_HOST` as `secrets.SSH_USER` with `secrets.SSH_PRIVATE_KEY`
   (GHA uses key auth even though humans use password auth), then on the box:
   `docker login ghcr.io` (secrets `GHCP_PAT`/`GHCP_USER`) →
   `cd /root/coursestools` → `docker compose pull backend` →
   `docker compose up -d backend` → `docker image prune -f`
   (docker-build.yml:60-72).

Watch a deploy with `gh run watch` / `gh run list --workflow docker-build.yml`.
Rollback: no tagged history — image is only pushed as `:latest`, so roll back
by reverting the commit on master and letting CI redeploy.

## Logs / observability

- Prod profile logs **JSON to stdout** (logstash encoder,
  `logback-spring.xml:12-35`); dev profile is human-readable.
- The backend container ships stdout to **Loki** at `loki.winworldteam.com`
  via the docker Loki logging driver (configured in the prod compose on the
  box, not in this repo).
- Query logs in **Grafana on ct-logs** (`http://77.232.135.132:3000`), Loki
  datasource `:3100`.
- Metrics: actuator on `:8081` exposes `health, prometheus, metrics, info`
  (`configs/actuator.yml:5,22`); Prometheus runs on ct-logs `:9090`.

## Egress / proxy split

None — all outbound calls go direct from ct-backend: Stripe (live),
CryptoCloud, Payeer, TV access bot `http://45.141.184.24:4320`
(`/open`, `/username_changer`, `/withdrawal`), alert bot
`http://193.160.209.73:8080/api/alert/message`, SMTP `smtp.timeweb.ru:25`
(all URLs in `backend-v2/.env:23-48`).

## Backups

TODO(operator): no backup job is visible from the repo. Postgres data lives
in an external/undocumented database location; document the provider and dump
schedule/destination without copying credentials.

## Things to NEVER touch on this box

- `plausible` and `pixel-canvas` containers — shared/unrelated services
  co-located on ct-backend.
- `caddy` — TLS/reverse proxy for everything; restarting it drops the site.
- `frontend` container — owned by a different repo/pipeline.
- `/root/coursestools/.env` — prod secrets; never overwrite from the local
  dev `.env` (they differ: local has localhost URLs, `COOKIE_SECURE=false`).
- The whole compose stack: operate on `backend` by service name only.
