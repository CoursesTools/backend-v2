# Dev Flow

The steps EVERY change follows. Skipping one is the #1 cause of
"this worked in dev but broke on prod" debugging sessions — and here
**merge = prod deploy**, so there is no staging net to catch you.

## 0. Gate-in

Run the audit gate (`audit-gate.md`) BEFORE you start work: on a
fresh `master`, `./gradlew build` must be green. If master isn't
green, you're about to add changes to a broken base — and since any
push to master deploys, a broken master may mean a broken prod. Fix
master first.

## 1. Understand (gain context from llm-corner)

- Re-read the relevant `docs/architecture/` file. Code can shift;
  the doc may have shifted too.
- Check `docs/reference/gotchas.md` for the symptom or area — this
  codebase has sharp edges (retry-config names, TV bot timezone,
  Stripe-owned expiry) that have each cost a debugging session.
- Grep `history/changelog/` for recent related entries.
- For anything touching servers, logs, or deploys, read
  `docs/operations/servers.md` and `docs/operations/log-playbook.md`.

## 2. Plan (if non-trivial)

A change spanning >1 commit OR touching contracts (API shapes, DB
schema, external bot payloads, Stripe webhooks) OR introducing new
patterns gets a PLAN file in `active/plans/`. Template at
`../../templates/plan.md`. Operator reviews the plan before code
lands.

## 3. Branch

Branch off `master`, named by type: `feat/…`, `fix/…`, `chore/…`
(observed: `feat/tv-retry-jobs`, `fix/cryptocloud-workflow-rebuild`,
`chore/revise-backend-logic-apr`). Never work directly on master —
pushing it deploys prod (`git.md`, Push safety).

## 4. Implement

- Atomic commits per `git.md`; Flyway migrations in their own commits.
- For each commit: code + tests + changelog row + (if a contract
  changed) updated architecture doc.
- Follow the patterns in `code.md` (Lombok DI, MapStruct, @Retry with
  durable fallback, DTO-per-domain).

## 5. Build & verify locally

- `./gradlew build` after every commit (not just at the end) — it
  compiles and runs the full JUnit 5 suite; CI runs the identical
  command, so red local = red deploy.
- Targeted iteration: `./gradlew test --tests "ClassName.method"`.
- For runtime behaviour: exercise the REAL flow end-to-end, including
  the side effect (the subscription row changes status, the TV bot
  request actually fires, the email actually sends). Build passing is
  NECESSARY but not SUFFICIENT — never report a feature done while
  it's built-but-unwired (`code.md`, Deliverables).
  Local run: `./gradlew bootRun` (needs local Postgres/Redis — see
  `LOCAL_SETUP.md` / `DOCKER_DB.md` in `../../../user-corner/guides/`).

## 6. Review → PR → merge (= deploy)

- Open a PR into `master` with Summary / Why / Test plan (`git.md`,
  PR flow). Get it reviewed; land review fixes as new commits on the
  branch.
- The OPERATOR merges. Merging triggers
  `.github/workflows/docker-build.yml`: gradle build → push
  `ghcr.io/coursestools/backend:latest` → SSH to the backend VPS →
  `docker compose pull backend && docker compose up -d backend`
  (docker-build.yml:67-72). Treat the merge button as a deploy button.

## 7. Verify on prod

Right after the deploy job finishes:

- Watch the backend logs — Grafana/Loki on the logs VPS (the prod
  backend container ships JSON logs via the Docker Loki driver), or
  directly: `ssh ct-backend 'docker logs --tail 200 backend'`.
  Playbook: `docs/operations/log-playbook.md`.
- Confirm clean startup (Flyway applied, no stacktraces), then
  exercise the changed endpoint/flow against prod and watch its log
  line land.
- If a migration shipped: verify the actual columns/tables exist on
  the prod DB, not just that the app started.
- Remember the prod container runs UTC while the host is
  Europe/Moscow — read log timestamps accordingly
  (`docs/operations/servers.md`).

## 8. Close out

End-of-session checklist at `protocols/lifecycle.md`. The high points:
- Write the handoff.
- Archive shipped plans → `history/plans/`.
- Sweep your inbox; reply to anything that's been sitting.
- Log to the monthly changelog file.
- Write any DEC for cross-cutting decisions.

## Per-commit checklist

- [ ] Feature works (build green, real path exercised — side effect
      included)
- [ ] `./gradlew build` clean
- [ ] Tagged changelog entry added
- [ ] Architecture doc / `contracts.md` updated if a contract changed
- [ ] Decision record written if a significant choice was made
- [ ] Plan task marked complete
- [ ] No secrets in staged files (`.env` never staged)
- [ ] Commit message follows `git.md` format

## When NOT to do something

- Don't add features / refactor / abstract beyond what the task
  requires. A bug fix doesn't need surrounding cleanup; a one-shot
  doesn't need a helper. Three similar lines is better than a
  premature abstraction.
- Don't add error handling, fallbacks, or validation for scenarios
  that can't happen. Trust internal code and framework guarantees.
  Validate at system boundaries only (controllers, webhooks, bot
  responses).
- Don't introduce feature flags or backwards-compatibility shims
  when you can just change the code.
- Don't write WHAT comments. Names already do that. Comments
  explain WHY — invariants, surprises, workarounds for specific
  bugs (see the fallback-overload comments in
  `ActivatingSubscriptionService.java` for the house style). If the
  code is obvious without the comment, delete it.
