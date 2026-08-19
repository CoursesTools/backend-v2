# history/ — Append-Only Archive

Never read at session boot; consult on demand (archaeology, audits,
"when did X change"). Files move in and never out — with one
exception: anything agents must *routinely* consult is misfiled here
and gets **promoted** to `../docs/reference/` (DEC-001).

| Dir | Contents | Entry point |
|---|---|---|
| `changelog/` | monthly tagged change stream | `changelog/README.md` — grep, don't read |
| `handoffs/` | session-end notes | `handoffs/INDEX.md`, newest first — **top entry is the live session entry point** |
| `plans/` | shipped/superseded plans | `plans/INDEX.md`, newest first |
| `audits/` | formal audit reports (all agents) | filenames `AUDIT-YYYY-MM-DD-<slug>.md` |
| `inbox/` | resolved inter-agent + operator messages | dated filenames, prefix per `../protocols/messaging.md` |
| `mockups/` | operator-supplied design references | dated filenames |

New archive streams (e.g. `reviews/`, `probes/` for superseded
investigation snapshots, `phase-logs/` for archived taskboard
reports) earn a subdirectory + a row here when the first file lands
— each is grep-only, never boot-read.

## Why an archive

1. **Decision trace.** "Why did we do X?" → grep `decisions/` first,
   `history/handoffs/` second.
2. **Pattern mining.** New bugs often look like old bugs.
   `docs/reference/gotchas.md` is the curated index; this is the raw
   source.
3. **Audit reproducibility.** Every audit produces a report; reports
   live forever.

## What does NOT go here

- Live in-flight work → `../active/`.
- Evergreen facts → `../docs/`.
- Decisions (evergreen, not historical) → `../decisions/`.
- Dated-but-authoritative material agents consult during normal
  work → `../docs/reference/`.

## Filing rules

- One file per artefact (handoff, plan, audit). No "weekly summaries".
- Each subdirectory's `INDEX.md` is newest-first. Adding a row to
  the INDEX is part of archiving — don't skip.
- Files are immutable once archived. Corrections go in a new file
  with `(supersedes …)` in the header.

## Keeping the repo lean (optional, when history/ gets big)

Old bulk that is never boot-read AND has ZERO inbound references
from evergreen/boot-read docs may be moved to an off-repo archive
(external drive / cold storage) with a MANIFEST file at the archive
location and a dated note here saying what moved and where. The
decision WHY-trail (plans, handoffs, resolved inbox, referenced
audits) stays in the repo. Restore any archived file by copying it
back to its original path.
