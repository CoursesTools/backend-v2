# Lifecycle — Session Close and Archiving

The rule that keeps `active/` honest. Without an explicit trigger,
"active" plans accumulate long after they ship (the reference project
hit 12 "active" files, ~10 already shipped, before adopting this).
The trigger is this checklist; running it is part of finishing a
session, exactly like the audit gate is part of committing.

## Session-close checklist

Run when ending any working session that changed code or docs:

1. **Handoff.** Write
   `../history/handoffs/YYYY-MM-DD-<slug>.md` from
   `../templates/handoff.md`, then add a summary entry at the TOP of
   `../history/handoffs/INDEX.md` (newest first — the top entry is the
   next session's entry point).
2. **Plans.** Any plan in `../active/plans/` that SHIPPED this
   session: set its final status line, `git mv` it to
   `../history/plans/`, add a row (newest first) to
   `../history/plans/INDEX.md`. Deferred remainders go to
   `../active/backlog.md`, not into a zombie plan.
3. **Inbox sweep.** Move resolved messages from every
   `../agents/<agent>/inbox/` to `../history/inbox/`. Unactioned
   findings → `../active/backlog.md` first (per `audits.md`).
4. **Changelog.** Confirm every commit this session has its tagged
   entry in `../history/changelog/<YYYY-MM>.md`.
5. **Decisions.** Any significant choice made mid-session that isn't
   yet a `DEC-NNN` file → write it now (`../templates/decision.md`).
   The test: would the next agent need the reasoning to safely change it?
6. **Taskboard.** Done/blocked task files reviewed and archived to
   `../history/phase-logs/<plan>/`.
7. **Docs drift.** If anything you shipped contradicts an evergreen
   doc, fix the doc NOW (drift is a P1 defect of the doc system —
   `../docs/conventions/docs-style.md`).

## Health invariants (any agent may fix on sight)

- `../active/plans/` holds 0–2 plans. More means someone skipped step 2.
- Every `../agents/<agent>/inbox/` trends toward empty.
- The top entry of `handoffs/INDEX.md` describes the actual latest state.
- New month → create `../history/changelog/<YYYY-MM>.md`, register it
  in `../history/changelog/README.md`.

## Archive semantics

- `history/` is **append-only**: files move in, never get edited
  (except a final status line set at archive time) and never move out.
- Exception: something in `history/` that agents must routinely
  consult is misfiled — promote it to `../docs/reference/` (DEC-001).
- Internal links inside archived files are NOT rewritten. If the
  corner was migrated from an older docs layout, archives keep their
  old internal paths — document the old→new mapping in `../README.md`
  instead of editing history.
