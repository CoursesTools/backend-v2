# conventions/ — How We Work

One subject per file.

| File | Subject |
|---|---|
| `mindset.md` | Operating mindset + the hard invariants every agent obeys |
| `contracts.md` | Critical system contracts C1–C8 |
| `code.md` | Code style + the hard delivery/UI rules (end-to-end, responsive UI, humanized copy, migrations, egress) |
| `git.md` | Commit format, attribution, branch policy, safety rules |
| `dev-flow.md` | The steps every change follows, gate-in to close-out |
| `audit-gate.md` | Pre-commit quality gate script |
| `docs-style.md` | How llm-corner itself stays healthy (filing, naming, atomization) |

Domain-specific convention files join this table as they emerge (one
subject per file) — e.g. a data-quality rulebook for a pipeline, or a
pointer file for upstream repos your services fork from.

Agent **coordination** rules (subagents, taskboard, messaging, audits,
session lifecycle) live in `../../protocols/`, not here.
