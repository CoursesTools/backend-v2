# Subagent Development Playbook

How a chief agent parallelizes work with **in-session subagents**
(the `Agent` tool or equivalent). This is the file to read when the
operator says *"do X, use subagents if possible to speed up work."*

Cross-SESSION parallelism (separate terminals) is `taskboard.md`, not
this file. The two compose: a chief in one terminal can use both.

## 1. Decision gate — should you parallelize at all?

Walk this in order; stop at the first NO and work solo.

1. **Does the work decompose into independent units?** Independent =
   zero owned-file overlap AND neither unit needs the other's output.
   If units are sequential, subagents add only overhead.
2. **Is each unit bigger than its briefing?** A self-contained brief
   costs ~10 min of writing and review. A unit under ~15 min of
   focused solo work is cheaper done inline.
3. **Is the decision-shape closed?** Subagents execute on a brief;
   they don't decide architecture. If the design is still open, settle
   it first (or dispatch a read-only `Explore`/`Plan` agent to gather
   signal — research parallelizes even when implementation doesn't).
4. **Do you have verification capacity?** You must read every diff a
   subagent produces. If N diffs would exceed what you can genuinely
   review this session, lower N.

Tell the operator the outcome in one line: either the split
("3 subagents: actor JS / CRUD handler / CSS refactor") or why solo is
faster. Don't silently ignore a "use subagents" request — push back
with the reason (per `../docs/conventions/mindset.md`).

## 2. Setup — file ownership before anything runs

- Write the split as OWNED / READ-ONLY / FORBIDDEN lists per subagent
  **before dispatching**. Zero overlap between OWNED lists — if two
  units want the same file, serialize them or re-split the file.
- The chief ALWAYS owns shared infra files (list in `roles.md`).
  Subagents request shared-file changes in their report; the chief
  applies them.
- Track the split in the active plan (or inline in conversation for
  small jobs) so a later session can reconstruct who touched what.

## 3. Briefing — subagents start cold

A subagent has NO conversation context. Every brief includes
(template: `../templates/subagent-brief.md`):

- **Goal** in one sentence.
- **Exact files** to create/modify with paths (+ line numbers when
  known). Never delegate "find the right place".
- **OWNED / READ-ONLY / FORBIDDEN** lists.
- **Conventions pointers**: `../docs/conventions/code.md` (+ the
  Frontend section for UI work), relevant architecture doc, relevant
  `../docs/reference/gotchas.md` entries.
- **Agent type**: write/edit → `general-purpose`; read-only research →
  `Explore`; design exploration → `Plan`.
- **Definition of done**: what to verify (the project gate commands
  from `../docs/conventions/audit-gate.md`, specific tests) and the
  exact report shape expected back (files touched, build status,
  shared-file requests, suggested changelog line). For feature work,
  require the subagent to TRACE + PROVE the real flow end-to-end —
  gating the build alone does not prove the feature is wired
  (`../docs/conventions/code.md`, Deliverables) — and re-verify its
  claims yourself.

Dispatch independent subagents **in parallel** (single message,
multiple Agent calls). Dependent work runs sequentially.

## 4. Integration — trust the report, verify the diff

For each returned subagent, the chief:

1. Reads the actual diff of every OWNED file. The report describes
   intent; the file describes reality.
2. Applies requested shared-file changes (deps, route registration,
   template registry).
3. Runs the unified build + vet + audit gate on the merged result.
4. Exercises the path when feasible (endpoint, template render, test).

## 5. Commit cadence + attribution (safe-changes rule)

- **One feature = one commit**, committed by the chief — subagents
  never run git. Integrate and commit feature-by-feature as units
  verify; do NOT batch the whole fan-out into one mega-commit. A bad
  unit then reverts alone.
- Attribution in the commit body:
  `Implemented-By: <label> (subagent)` — e.g. `Implemented-By: W2
  (general-purpose subagent)`. The `Co-Authored-By` trailer stays the
  chief's model per `../docs/conventions/git.md`.
- Each feature commit carries its **tagged changelog entry** (the
  subagent's suggested line, edited by the chief) and any doc updates
  (Contract C7). Changelog format: `../history/changelog/README.md`.
- Commits still require operator approval unless pre-approved in the
  plan cadence (hard invariant: no auto-commit).

## 6. Failure handling

- A subagent that reports blocked or returns garbage: don't iterate
  blindly — fix the brief (it was almost certainly under-specified)
  or pull the unit inline.
- A subagent that touched files outside OWNED: discard those hunks
  (`git checkout -- <file>` for unstaged damage), note it, tighten the
  next brief. Never integrate out-of-scope changes silently.
- Two subagents conflicting on a file means the setup step failed —
  resolve manually, record what the split missed.

## 7. Worked example

Plan-NN sprint K — after the schema migration commit landed, the
chief dispatched three parallel subagents: (1) external-actor edits
in a separate sub-repo (different language, no overlap with the main
codebase); (2) a new CRUD handler + templates (own handler file +
own template dir); (3) a CSS-only refactor (owns `app.css` only).
Zero file overlap. All three returned reports; the chief read diffs,
ran the audit gate, made three attributed commits per the planned
cadence.

(Replace this with a real worked example from your own project once
you have one — concrete prior runs land better than abstractions.)

## Cardinal rules

1. Brief like a colleague who just walked in — self-contained, no
   conversation shorthand.
2. File ownership is absolute.
3. Subagents never commit.
4. Verify the diff, not the report.
5. Parallel only when truly independent.
6. One feature = one attributed commit with its changelog entry.
