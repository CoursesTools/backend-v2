# Creating a New Agent

The checklist for onboarding any new LLM agent (Gemini, a second
Claude flavor, anything). An agent is not "created" until every item
exists — partial agents break the messaging contract.

## 1. Workspace — `llm-corner/agents/<name>/`

Start by copying the scaffold: `cp -R agents/_template agents/<name>`
(then fill in the README). Create ALL of these (structure contract in
`../agents/README.md`):

| Path | Required | Purpose |
|---|---|---|
| `agents/<name>/README.md` | yes | what's agent-specific here + the agent's default role; facts NEVER live here (DEC-001) |
| `agents/<name>/inbox/` (+ `.gitkeep`) | yes | messages addressed TO this agent — its only incoming channel (`messaging.md`) |
| `agents/<name>/notes/` (+ `.gitkeep`) | yes | the agent's personal notes: observations, working decisions, self-reminders (rules in `../agents/README.md`) |
| `agents/<name>/prompts/` | yes | paste-ready startup prompts for each role the agent plays |
| `agents/<name>/operating-mode.md` | only if defaults differ | deviations from `protocols/` defaults (e.g. an auditor-first agent) |

## 2. Root entry shim — `<NAME>.md` at repo root

The file the agent's tooling auto-reads (`AGENTS.md` serves Codex and
any agent without its own convention; create e.g. `GEMINI.md` only if
the tooling demands a specific filename). Copy `AGENTS.md` verbatim
and adjust the identity line + workspace pointer. Keep it ~25 lines:
project one-liner, hard invariants, "gain context via
`llm-corner/README.md`". **Shims never accumulate facts** — that's the
rot DEC-001 killed.

## 3. Registration (same commit)

- `protocols/messaging.md` — add the inbox to the inbox table.
- `llm-corner/README.md` — routing table already points at
  `agents/<you>/`; nothing to add unless the agent introduces a new
  artifact type.
- Changelog entry tagged `[docs][<creator-agent>]`.

## 4. Acceptance test

Cold-start the new agent with: *"gain project context from llm-corner,
then summarize the current state of the project."* It must cite the
latest handoff, active plan(s), backlog, and contracts unprompted, and
correctly state where its inbox and notes live. If it can't, fix the
shim or this checklist — not the agent.

## Decommissioning

Don't delete an agent's workspace — move it under
`history/agents/<name>/` (create on first need), sweep its inbox to
`history/inbox/`, and remove the root shim + messaging registration.
