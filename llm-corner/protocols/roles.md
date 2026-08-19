# Roles

The CoursesTools backend (CT) is built by LLM agent sessions on a shared
filesystem. There is NO shared runtime and NO network bus — files are
the message bus. Three roles, independent of which model plays them:

## Chief (a.k.a. Teamlead / TL)

The session the operator talks to. Exactly one per workstream.

1. **Plans.** Owns the active `PLAN-NNN` in `../active/plans/`.
2. **Delegates.** Dispatches in-session subagents (`subagents.md`) or
   creates taskboard tasks for parallel sessions (`taskboard.md`).
3. **Owns shared files.** Only the chief modifies the shared infra
   files: `build.gradle.kts` / `settings.gradle.kts`,
   `src/main/resources/application.yml` and
   `src/main/resources/configs/*.yml`, everything under
   `config/` (Spring + security config, incl.
   `config/security/SecurityConfig.java` / `PublicUrlsHolder.java`),
   the Flyway migration sequence in
   `src/main/resources/db/migration/` (version numbers are a shared
   resource — two workers claiming the same `V<n>` breaks the build),
   and `.github/workflows/docker-build.yml`. Workers request changes
   via their reports.
4. **Integrates and verifies.** Unified build, audit gate, reads the
   actual diffs — reports are claims, diffs are facts.
5. **Commits.** Workers and subagents NEVER commit. The chief makes
   all commits (atomic, per `../docs/conventions/git.md`), with
   implementer attribution.
6. **Documents.** Changelog, plan checkboxes, handoff at session end
   (`lifecycle.md`).

## Worker

An independent implementing session (separate terminal) — see
`taskboard.md` for its full lifecycle. One task per session unless the
chief says otherwise. In-session subagents are NOT workers in this
sense; their rules are in `subagents.md`.

## Auditor

An independent reviewer session. Evidence-first;
findings need file:line + impact + remediation. Never trusts another
agent's summary — re-opens current files. Full rules: `audits.md`.
Any model can audit; the audit format is agent-neutral.

## Who plays what

Role ≠ model. The operator may run one model as chief and another as
auditor, collapse chief+worker into one session (the common mode for
most work), or spin any future model into any role. Per-agent
specifics (startup prompts, operating modes) live in
`../agents/<name>/`.
