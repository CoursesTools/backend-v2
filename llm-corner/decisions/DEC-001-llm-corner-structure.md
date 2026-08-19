# DEC-001 — llm-corner Documentation System

**Status:** ACCEPTED
**Date:** 2026-07-12
**Decision by:** the operator

## Context

Project knowledge was scattered across a dozen root-level dirs — `claude-docs/`, `claude-plans/`, `claude-git/` (per-commit message logs), `claude-msgs/` (ad-hoc inter-agent messages to admin-web), `claude-payment-issue/`, `codex-corner/`, plus root `CLAUDE.md`, `DOCKER_DB.md`, `LOCAL_SETUP.md`. There was no single canonical source: facts drifted between files, and every session began with a "deep-dive into this part of the code" before real work — burning tokens and context re-deriving things that were already known but not written down anywhere trustworthy.

## Decision

Adopt the llm-corner documentation system: a single directory at `llm-corner/` that holds all project facts an LLM agent needs, organised by content type × lifecycle. Root `CLAUDE.md` / `AGENTS.md` shrink to ~20-line entry shims pointing at `llm-corner/README.md`, the single routing table. It is the shared knowledge hub for every agent (Claude, Codex, …) — each keeps only a personal workspace under `agents/<name>/`; all durable facts are global.

Structure: `docs/` (evergreen), `protocols/` (agent coordination), `decisions/` (this directory), `templates/`, `active/` (in-flight), `history/` (archive), `agents/` (personal workspaces). Operator-supplied material (setup guides, mockups) lives in a sibling `../user-corner/`, not here.

Bootstrap recipe: `llm-corner-kickoff/` template kit.

## Consequences

**Positive:**
- New/returning agents reach full context in ~10 min via a documented boot sequence — no more "deep-dive the code first."
- Facts live in one place; "which file is canonical?" is gone. Route by content type, not author.
- Lifecycle is explicit: `active/` trends to empty; `history/` is append-only and never boot-read, so boot cost stays flat as history grows.

**Negative:**
- Initial setup cost (this session).
- Ongoing discipline: the session-close checklist (`protocols/lifecycle.md`) isn't optional — skip it and `active/` rots.

**Alternatives rejected:**
- Per-agent docs (the old `claude-*` / `codex-corner` split) — drift was exactly the problem being fixed.
- A plain `docs/` folder by topic — fine for humans, but the agent boot sequence + routing table needs its own home.

## Migration notes

Built from the `llm-corner-kickoff` template. A multi-agent (Fable 5) sweep of the codebase generated the `docs/` content with every claim verified against current code. The scattered `claude-*` and `codex-corner` dirs were mined for durable facts (folded into `docs/reference/gotchas.md`, the architecture docs, and `history/changelog/`), verified against code, then deleted (recoverable via git). User-facing setup docs (`DOCKER_DB.md`, `LOCAL_SETUP.md`) moved to `../user-corner/guides/`. Root `CLAUDE.md`/`AGENTS.md` reduced to shims.
