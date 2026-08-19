# Changelog — Format and Lookup

One stream, monthly files (`YYYY-MM.md`), tagged one-line entries.
Newest entries at the TOP of each file. Grep-indexed — never read
whole files. Why one stream and not per-domain folders: grep does
the sharding; folders multiply file churn and split cross-domain
changes.

## Entry format

```md
- [domain][agent] <what changed + why it matters> (`<commit sha>`; DEC-NNN if applicable)
```

- **Domain tags** (extend freely, lowercase): short subsystem tags
  matching `docs/architecture/` files plus the evergreen ones —
  `ui`, `db`, `worker`, `infra`, `deploy`, `security`, `docs`.
  Multi-domain entries carry multiple tags: `[ui][<subsystem>]`.
- **Agent tag**: `[<agent-name>]` — this is where agents see each
  other's changes; the changelog doubles as the cross-agent
  activity feed.
- **Every commit gets exactly one entry, in the same commit.**

## Lookup — don't read, grep

```sh
# everything that ever touched <subsystem>
grep -n "\[<subsystem>\]" llm-corner/history/changelog/*.md

# what <agent> changed in a given month
grep -n "\[<agent>\]" llm-corner/history/changelog/<YYYY-MM>.md

# entries that link a decision
grep "DEC-" llm-corner/history/changelog/*.md
```

Read the matching lines, follow the commit sha or DEC link for
depth. Never page through a monthly file looking for one change.

## Files

| File | Period |
|---|---|
| `2026-08.md` | 2026-08 |
| `2026-07.md` | 2026-07 — first month (doc-system bootstrap); includes a pre-changelog backfill section covering notable 2026-04 … 2026-05 commits |

New month → create the file, add the row here (part of the
lifecycle checklist). A closed month's file is immutable.

## What does NOT belong here

- "Refactored X for clarity" without a why — not worth recording.
- Per-commit micro-fixes that landed under a single feature — the
  parent feature's entry covers them.
- Anything that already lives in a DEC or handoff — link, don't copy.
