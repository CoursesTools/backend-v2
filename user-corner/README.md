# user-corner — Operator inputs

Human/operator-supplied material for the **CoursesTools backend**: setup guides, mockups, and raw specs. This is the counterpart to `../llm-corner/` (the agents' knowledge hub): things the operator hands to agents live here; things agents derive and maintain live in `llm-corner/`.

Agents: `llm-corner/README.md` routes here for "operator-supplied specs / guides / mockups." Treat these as **inputs**, not source of truth — when a guide here and an evergreen doc in `llm-corner/docs/` disagree about how the code works, the code (and the doc that cites it) wins; flag the stale guide.

## Layout

```
guides/    setup & local-dev guides (run the app, spin up the DB)
mockups/   UI mockups / design references the operator drops in
```

## Contents

- `guides/LOCAL_SETUP.md` — local development setup (run the backend locally).
- `guides/DOCKER_DB.md` — bring up PostgreSQL (and friends) via Docker for local work.
- `mockups/` — empty until the operator adds design references.

> Servers, deploy, and prod DB access are **not** here — those are agent-operational facts in `../llm-corner/docs/operations/servers.md`.
