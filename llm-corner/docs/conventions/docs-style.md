# Documentation Style — How llm-corner Stays Healthy

These rules keep the documentation system flat-fast to navigate and
cheap in tokens. They are the constitution of `llm-corner/`; when a
filing question comes up, answer it from here, in order.

## Filing rules

1. **Route by content type, not by author.** A fact, decision, audit,
   or handoff valuable to all agents lives in the global tree
   (`docs/`, `decisions/`, `history/`). Authorship is a header field
   (`From: Codex`), never a directory. `agents/<name>/` holds ONLY
   genuinely agent-specific material (role prompts, operating mode).
2. **Every fact lives in exactly one file.** Everything else links to
   it. Never create per-agent "context snapshots" — they rot in days
   (see DEC-001 for the post-mortem).
3. **Location = lifecycle.** `docs/` is evergreen current state;
   `active/` is in-flight and trends toward empty; `history/` is
   append-only and never read at session boot. Anything in `history/`
   that agents must routinely consult is misfiled — promote it to
   `docs/reference/`.
4. **One topic per file.** The split trigger is "covers two subjects",
   not line count. Target ≤ ~200 lines per evergreen doc; past ~300
   lines, look hard for a hidden second subject.
5. **2-hop rule.** Any question must route to the right file in ≤ 2
   hops from `llm-corner/README.md` (README → section index → file).
   If a doc needs 3 hops, fix the index, not the reader.

## Naming

- Evergreen docs: `lowercase-kebab.md`, named by subject, never dated.
- Dated artifacts (history, inbox, probes, mockups):
  `YYYY-MM-DD-<slug>.md`. Message prefixes (`AUDIT-REQ-`, `RESP-`,
  `MSG-`) per `../../protocols/messaging.md`.
- Plans: `PLAN-NNN-<slug>.md`. Decisions: `DEC-NNN-<slug>.md`.
  Numbers never reused.

## Content rules

- **Evergreen docs describe current state only.** "We did X" with no
  forward impact goes in the changelog, not the doc.
- Every evergreen doc follows the shape: purpose → current contract →
  code pointers → gotchas. Optional footer: `## Change history` with
  ≤ 5 pointer lines (`YYYY-MM-DD — one-liner — commit/DEC-NNN`);
  oldest lines drop when full (git keeps everything).
- **Prefer fewer, sharper docs.** Future agents must never reconcile
  two different answers.
- Don't duplicate what the code or schema states; link with a path
  (`internal/workers/dispatcher.go`) instead.

## When to run a docs pass

- After closing a plan (mandated by `../../protocols/lifecycle.md`).
- After schema/route/settings/contract changes.
- When two docs disagree — fix immediately; drift is a P1 defect of
  this system.

## Change history

- 2026-07-12 — created at corner bootstrap (DEC-001).
