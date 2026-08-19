# CLAUDE.md — CoursesTools backend Entry Shim

Production Spring Boot backend for **CoursesTools** (CT-Pro): a subscription SaaS that grants paying users TradingView indicator access via an external "TV access bot". Java 17 / Spring Boot 3.2.4 / Postgres / Redis. The company runs on it — treat every change as prod.

**Gain project context from `llm-corner/README.md`** — it is the single router for everything: boot sequence, conventions, contracts, architecture, servers, protocols, current state. Do this before any task. All facts live there, not here (`llm-corner/decisions/DEC-001-llm-corner-structure.md`).

Claude's personal workspace is `llm-corner/agents/claude/`; read its inbox during the router's boot sequence.

## Hard invariants (apply even if you read nothing else)

1. **Never commit or push automatically.** Pushing `master` auto-deploys the live payment backend (GitHub Actions → build image → `docker compose pull backend && up -d`). Stop after verification and ask, unless the operator explicitly asked for commit/push in the current task.
2. **Gate before any work and before every commit:** `./gradlew build` — all tests PASS. There is no separate linter; the build IS the gate.
3. **Never commit secrets, and treat migrations as immutable.** `.env` holds live Stripe keys, DB creds, and VPS passwords. Never edit an applied Flyway migration — add a new one.
4. **Don't touch the co-located non-backend services on the prod box** (`plausible`, `pixel-canvas`, `caddy`) unless explicitly asked.

<!-- Keep this shim ~20 lines; it is auto-injected into every session — facts belong in llm-corner/, not here. Full reasoning for these invariants: llm-corner/docs/conventions/mindset.md. -->
