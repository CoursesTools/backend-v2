# llm-corner — Single Source of Truth for All Agents

You are an LLM agent on **CoursesTools backend** (CT): a subscription SaaS that grants paying users access to premium TradingView indicators. Users sign up with a verified TradingView nickname, buy a CT-Pro subscription (or a 7-day trial), and an external "TV access bot" grants/removes their indicator access; a tiered referral-cashback partnership sits alongside.

**Stack:** Java 17 + Spring Boot 3.2.4 + PostgreSQL + Redis + Gradle. Layered Controller → Facade → Service → Repository; event-driven (`@TransactionalEventListener` + `@Async`); MapStruct, Lombok, Flyway, resilience4j.

This directory is the ONLY routing table. Every fact lives in exactly one file here; if two docs disagree, that's a P1 defect — fix it (`docs/conventions/docs-style.md`).

## Boot sequence — "gain project context from llm-corner"

Read in order (≈10 min; steps 5–7 are per-task):

1. **This file** — you're here.
2. `docs/conventions/mindset.md` — operating standards + the **hard invariants** (the absolute never-do rules).
3. `docs/conventions/contracts.md` — system contracts C1–CN.
4. `history/handoffs/INDEX.md` — the TOP entry is the latest session state; open that handoff.
5. `active/plans/` + `active/backlog.md` — what's in flight + known-open.
6. `agents/<you>/inbox/` — anything waiting on you (skim `agents/<you>/notes/`).
7. `git log --oneline -10`.
8. Before touching code: run the gate — **`./gradlew build`** (compiles + all tests; there is no separate linter — the build IS the gate).

Multi-agent? Also read `protocols/roles.md` and your corner `agents/<you>/README.md`.

## Routing table — "I need X"

| You need... | Read... |
|---|---|
| What the product is / who uses it | `docs/product/overview.md` |
| How a subsystem works | `docs/architecture/README.md` → subsystem file |
| **Subscriptions + the TradingView access bot** (grants, trial, grace, retry queue, the +1d buffer) | `docs/architecture/subscriptions-and-tradingview.md` |
| Payments / orders (Stripe, Crypto, Balance) | `docs/architecture/payments.md` |
| Auth / OAuth / signup TV-name check | `docs/architecture/auth-and-security.md` |
| Partnership / referrals / alerts | `docs/architecture/partnership-referrals-alerts.md` |
| Admin surface (grants, TV retry admin, stats) | `docs/architecture/admin.md` |
| Entities, migrations, schedulers, events | `docs/architecture/persistence-scheduling-events.md` |
| Sharp edges / "this symptom looks familiar" | `docs/reference/gotchas.md` |
| Connected external services (bots, gateways, admin-web) | `docs/ecosystem/README.md` |
| **Servers: where prod lives, ssh, DB access, deploys** | `docs/operations/servers.md` |
| What you can drive directly + boundaries | `docs/operations/capabilities.md` |
| A bug was reported — first move | `docs/operations/log-playbook.md` |
| Code style | `docs/conventions/code.md` |
| Commit format / git safety (push = prod deploy) | `docs/conventions/git.md` |
| The steps every change follows | `docs/conventions/dev-flow.md` |
| Pre-commit quality gate | `docs/conventions/audit-gate.md` |
| Where a new doc/file belongs | `docs/conventions/docs-style.md` |
| Security posture | `docs/security/baseline.md` |
| WHY a past choice was made | `decisions/INDEX.md` |
| "Use subagents" — parallelizing work | `protocols/subagents.md` |
| Messaging another agent / cross-repo | `protocols/messaging.md` |
| Requesting/performing an audit | `protocols/audits.md` |
| Ending a session (mandatory checklist) | `protocols/lifecycle.md` |
| What changed in subsystem X | `grep "\[x\]" history/changelog/*.md` |
| Blank artifact to fill in | `templates/` |
| Your inbox / personal notes | `agents/<you>/inbox/` · `agents/<you>/notes/` |
| Onboarding a brand-new agent | `protocols/new-agent.md` |
| Operator-supplied specs / guides / mockups | `../user-corner/` |

## Layout (lifecycle × content type)

```
docs/        evergreen truth — current state only, one topic per file
protocols/   how agents coordinate (roles, subagents, messaging, audits, lifecycle, new-agent)
decisions/   DEC-NNN ADRs — append-only WHY log
templates/   blank artifacts (plan, handoff, task, audit-req, decision, subagent-brief)
active/      shared in-flight work: plans/ (≤2), taskboard/, backlog.md
history/     append-only archive — never boot-read (history/README.md)
agents/      per-agent workspaces: inbox/ + notes/ + prompts/ + operating-mode
```

Writers' rules: filing/naming/atomicity → `docs/conventions/docs-style.md`; archiving triggers → `protocols/lifecycle.md`. The two root shims (`../CLAUDE.md`, `../AGENTS.md`) are ~20-line pointers here — facts live in this tree, never in the shims.
