# Taskboard — Cross-Session Worker Protocol

For work split across **separate agent sessions** (multiple terminals,
multi-day work, or mixed-model worker pools). The board is
`../active/taskboard/`; the filesystem is the message bus — claiming
is an atomic file rename. For in-session parallelism use
`subagents.md` instead (cheaper, same ownership rules).

Status: optional infrastructure. Recent plans usually collapse
chief+worker into one session; the board activates whenever the
operator opens parallel sessions.

## File states

```
TASK-X.Y-pending.md   unclaimed, available
TASK-X.Y-active.md    claimed (rename IS the claim — first mover wins)
TASK-X.Y-done.md      completed, awaiting chief review
TASK-X.Y-blocked.md   worker stuck, needs chief
```

## Worker lifecycle

1. Boot per `../README.md` (gain context first — always).
2. List `*-pending.md`; read one fully; claim by renaming to `*-active.md`.
3. Implement ONLY files in the task's OWNED section. READ-ONLY files
   may be imported, never modified. FORBIDDEN files are untouchable.
4. Verify: run the project gate commands
   (`../docs/conventions/audit-gate.md`); a failure in another
   module is the chief's problem — note it, don't fix it.
5. Rename to `*-done.md`, replace content with the completion report.
   If stuck: `*-blocked.md` with blocker + proposed solution.
6. Stop. One task per session unless the chief instructed otherwise.
   Workers never commit, never touch shared infra files (`roles.md`),
   never modify the dependency manifest (request deps in the report).

## Task file format (chief creates)

```md
# TASK-X.Y — <short title>
Phase: <n>   Priority: <P0|P1|P2>   Depends on: <ids|none>   Status: PENDING

## Objective
<2-3 sentences; reference spec/plan section>

## Owned Files (CREATE or MODIFY only these)
## Read-Only Files (may import, must NOT modify)
## Forbidden Files
## Acceptance Criteria  <specific, testable>
## References           <spec/plan/architecture-doc sections>
## Changelog Entry      <one tagged line for the worker to confirm/adjust>
```

## Completion report format (worker replaces content)

```md
# TASK-X.Y — <short title> [DONE]
Worker: <label>   Status: DONE|PARTIAL|BLOCKED

## Files Created / Files Modified
## Build Status            build: PASS|FAIL / vet-lint: PASS|FAIL (+ notes)
## Shared File Requests    <exact change per file, e.g. a dependency line>
## Issues / Notes          <decisions made, edge cases found>
## Changelog Entry         <final tagged one-liner>
```

## Chief integration loop

1. Read every `*-done.md`; read the **diffs**, not just reports.
2. Apply shared-file requests; run unified build + vet + audit gate.
3. Commit per feature with `Implemented-By:` attribution
   (`../docs/conventions/git.md`), changelog entry included.
4. Archive done task files to `../history/phase-logs/<plan-or-phase>/`.
5. Unblock `*-blocked.md` (fix shared files or recreate the task);
   create the next pending batch.

## Parallelism rule

Tasks may run simultaneously only if their OWNED lists have zero
overlap, neither depends on the other's output, and neither modifies
shared files. The chief verifies before creating the batch.
