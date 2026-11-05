# DEC-008 — Pushes that only touch `llm-corner/` do not deploy

Status: ACCEPTED
Date: 2026-11-05
Author: Claude Code (operator-requested: "make changes to llm-corner not restart prod")

## Context

`.github/workflows/docker-build.yml` ran on every push to `master`. A docs-only merge to
`llm-corner/` (handoffs, backlog, changelog) therefore rebuilt the image and restarted the live
payment backend:
- about 25s of API downtime;
- Stripe webhooks deferred to retries;
- a "Backend started" ops alert.

On 2026-11-05 several such restarts happened in one session, and keeping the agent docs current was
being traded against production stability.

## Decision

- The workflow's `push` trigger has `paths-ignore: ['llm-corner/**']`. GitHub skips the run only if
  **every** changed file in the push is under `llm-corner/`. Any push that also changes code, config,
  migrations, the workflow, `CLAUDE.md`/`AGENTS.md` or anything else still builds, tests and deploys
  exactly as before.
- Manual `workflow_dispatch` is unchanged.
- Hard invariant 1 is unchanged: agents still never commit or push without the operator's go-ahead.
  Only the deploy side effect of docs-only pushes goes away.

## Consequences

- Contract C15 is reworded: a master push that touches anything outside `llm-corner/` deploys.
- A docs-only merge produces no workflow run and no "Backend started" alert. Do not wait for a
  deploy that will not come.
- `user-corner/` (operator guides) is not excluded; add it the same way if needed.
- The other repos (admin-web, frontend-v2, alert-bot-v2, alert-router) still deploy on every push to
  their default branch. Their agent notes are gitignored, so they never need such a push.
