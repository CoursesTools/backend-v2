# Agents

Per-agent personal workspaces.

## What lives here

Each `agents/<name>/` is owned by ONE agent. Contents:

```
agents/<name>/
├── README.md          # agent map + default role — never project facts
├── inbox/             # messages addressed TO this agent
├── notes/             # owner-write-only personal notes
├── prompts/           # paste-ready startup prompts per role
└── operating-mode.md  # optional: deviations from protocols/ defaults
```

## Structure contract

1. **`agents/<name>/` is personal workspace, not a fact home.** If
   you find yourself writing project facts here (architecture,
   conventions, current state) STOP and move them to the
   appropriate global tree (`docs/`, `active/`, `history/`).

2. **Inbox: messages TO this agent only.** Agent A's request to
   agent B lives in `agents/<b>/inbox/`; B's reply lives in
   `agents/<a>/inbox/`. Request and reply NEVER share a directory.
   See `../protocols/messaging.md`.

3. **Notes: promote-or-prune.** Personal notes are fine for
   session-spanning context the agent wants to remember. But anything
   useful to other agents must be PROMOTED to the global tree
   (gotchas, decisions, backlog). Stale notes get PRUNED in the
   session-close sweep.

4. **Empty inbox is the healthy state.** Messages move to
   `../history/inbox/` once resolved.

5. **The `_template/` directory is the starting point for a new
   agent.** Copy it (`cp -R agents/_template agents/<new-name>`),
   fill in the README, you're done. See `../protocols/new-agent.md`
   for the full checklist.

## Onboarding a new agent

Read `../protocols/new-agent.md` — mandatory checklist. The
high-level steps:
1. Copy `_template/` to `<new-name>/`.
2. Update `agents/README.md` (add the new agent to the index below).
3. Register the new inbox in `../protocols/messaging.md` (inbox
   table) and create the root entry shim — both mandated by
   `../protocols/new-agent.md`.

## Agents in this project

| Slug | Default role | Operating mode | Notes |
|---|---|---|---|
| `claude` | chief | default (`../protocols/` as written) | Claude Code sessions — usually the session the operator talks to |
| `codex` | worker / auditor | default | Codex sessions; pre-llm-corner audit history lives in `../history/audits/` (e.g. `2026-04-11-codex-payment-webhook-audit.md`) |

Workspace directories for both agents still need to be instantiated
from `_template/` (see `../protocols/new-agent.md`) — tracked in
`../active/backlog.md`.
