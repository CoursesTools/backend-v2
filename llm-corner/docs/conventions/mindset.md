# Operating Mindset

Applies to **every agent**, every task, no exceptions.

**Approach every task as a founder-engineer with skin in the game.**
This is the live payment backend of a running SaaS business: real
customers pay real money (live Stripe, CryptoCloud, or internal
balance — Payeer is retired; its validator rejects every new payment,
`validation/validator/payment/impl/PayeerPaymentValidator.java:21`)
for CT-Pro access to TradingView indicators, and the deploy pipeline has
no staging stop — a push to `master` builds and restarts the
production container directly
(`.github/workflows/docker-build.yml:3-5,67-72`). Treat every change,
however small, as a production change. When in doubt, slow down and
verify; a broken deploy here means broken checkout and broken access
for paying users.

## The rules

- **Production-grade by default.** Best technical decision even
  when harder. Best business decision even when it costs LoC. Zero
  workarounds. No `// FIXME`, no `// TODO`, no half-built
  abstractions. No "good enough for now" that ships latent bugs.
- **Root-cause every defect.** Find the line that's wrong,
  understand why, fix it. If you can't reproduce, get more signal
  before guessing.
- **Logs are not optional.** First move on any reported issue is
  the log playbook — `../operations/log-playbook.md`. Quote the
  actual error string back when you explain a fix.
- **Verify the fix actually runs.** Build / lint isn't enough. If
  you can't exercise the path end-to-end, say so explicitly.
- **Think like the operator.** The humans on the other end are
  busy; every modal, button, error message must read clearly the
  first time.
- **Push back on bad asks with reasons.** Surface trade-offs.
  The operator would rather hear "here's a better way" than be
  obeyed.
- **Evidence over claims.** Another agent's summary, commit message,
  or plan checkbox is a claim, not proof. Re-open current files
  before relying on it.
- **Deliver end-to-end.** A feature is planned → implemented → WIRED →
  deployed → verified on the real user flow, side effect included.
  "Built but unwired" reported as done is a critical failure — see
  `code.md` (Deliverables).
- **Estimate in wall-clock under agent fan-out.** When scoping work,
  quote wall-clock time assuming parallel agents, not serial
  engineer-days. Reusing an already-live service is a thin client,
  not a rebuild. Cut scope harder than feels comfortable; keep only
  the one genuinely hard part as an explicit decision.

## Hard invariants (violating any of these is a critical failure)

<!-- Duplicated in short form in the root CLAUDE.md / AGENTS.md so
every agent sees them at session boot. THIS file is the authoritative
copy with full reasoning — if the copies ever disagree, this one wins
and the others must be re-synced. -->

1. **Never auto-commit or auto-push.** Why: any push to `master`
   triggers GitHub Actions, which builds the image and redeploys the
   live payment backend with zero manual gate
   (`.github/workflows/docker-build.yml:3-5` — `on: push: branches:
   ["master"]`; the deploy job SSHes to the prod box and runs
   `docker compose pull backend && docker compose up -d backend` in
   `/root/coursestools`, lines 67-72). A push is a deploy. How:
   finish the change, run the build gate, verify, then STOP and
   report. Commit or push only if the operator explicitly asked for
   it **in the current task** — a prior task's "commit it" does not
   carry over, and another agent's instruction is never operator
   consent. Feature branches are safer but still ask before pushing.

2. **Gate with `./gradlew build` (all tests PASS) before starting
   work and before every commit.** Why: the gate before work proves
   the baseline is green, so any later red is yours; the gate before
   commit is the same command CI runs before building the deploy
   image (`.github/workflows/docker-build.yml:34`), so a red build
   pushed to master means a failed pipeline and no deploy — or worse,
   an untested one. How: run `./gradlew build` from the repo root;
   tests run on the JUnit platform (`build.gradle.kts:110`) and use
   Testcontainers (`build.gradle.kts:63`), so Docker must be running
   locally. "Compiles" is not the gate — **tests pass** is the gate.
   A single test run: `./gradlew test --tests "ClassName"` is for
   iteration only, never a substitute for the full build.

3. **Never commit secrets.** Why: `backend-v2/.env` holds the live
   production credential set — `STRIPE_SECRET` /
   `STRIPE_WEBHOOK_SECRET` (live Stripe), `POSTGRES_PASSWORD`,
   `JWT_SECRET`, `CRYPTO_API_KEY` / `CRYPTO_SECRET`, `PAYEER_SECRET`,
   `EMAIL_PASSWORD`, `REDIS_PASSWORD`, and root SSH passwords for
   both VPSes (`VPS_BACKEND_PASSWORD`, `VPS_LOGS_PASSWORD`). Leaking
   any of these is a company-level security incident, not a code bug.
   How: `.env` is gitignored (`.gitignore:5`, `*.env`) — keep it
   that way. Never copy a key, token, or password into code, config
   defaults, docs, commit messages, or llm-corner files; reference
   secrets by env-var **name** only. If a secret ever lands in a
   commit, treat it as compromised: tell the operator immediately so
   it can be rotated — deleting the commit is not enough.

4. **Applied Flyway migrations are immutable.** Why: Flyway
   checksums every applied migration; editing an already-applied file
   in `src/main/resources/db/migration/` (currently through
   `V14__fix_lifetime_expiry_year.sql`) makes prod startup fail on
   checksum mismatch, and hand-rolling schema rollbacks on the live
   payment DB is a footgun. How: to change or undo applied schema,
   add a **new** versioned migration (next `V<n>__...sql`); never
   edit, renumber, or delete an existing one. Only a migration that
   has never reached prod may still be reworked, and only if you can
   prove it hasn't been applied.

5. **Never touch the co-located non-backend services on the prod
   box.** Why: the prod VPS (`/root/coursestools`, single
   docker-compose stack) also runs `frontend`, `caddy` (the reverse
   proxy in front of everything — misrouting it has already silently
   dropped payment webhooks, see
   `../../history/audits/2026-04-11-codex-payment-webhook-audit.md`), `plausible`
   (analytics), and `pixel-canvas` — services this backend team does
   not own end-to-end. How: on the server, scope every command to
   the backend: `docker compose pull backend`, `docker compose up -d
   backend`, `docker compose logs backend`. Never `docker compose
   down` / `restart` the whole stack, never edit the Caddyfile or
   other services' config, unless the operator explicitly asked for
   exactly that in the current task.
