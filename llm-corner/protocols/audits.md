# Audits — Request, Perform, Close

Agent-neutral audit protocol — any model can audit. Audits are
evidence-first reviews of production risk. Findings are tied to code;
summaries come after findings.

## Requesting an audit

Write `AUDIT-REQ-YYYY-MM-DD-<slug>.md` into the auditor's inbox per
`messaging.md`, using `../templates/audit-request.md`. Scope it:
commit range or plan, the surfaces you want pressure on, and what you
already know is weak. Declare the response path.

## Performing an audit

- Prioritize production risk: auth, authorization, migrations, data
  integrity, background jobs, external APIs, deployment, user regressions.
- **Zero-trust re-inspection.** Agent replies, fix summaries, commit
  messages, and plan checkboxes are claims. Before accepting a fix:
  re-open current files, check the diff/commit range, re-run focused
  verification, confirm no new regression.
- User scope overrides agent-request scope. No visual/browser testing
  unless the user asks; for visual-only work, state that aesthetics
  weren't judged.
- Don't modify the implementer's files unless the user asks you to patch.
- Verification floor: `git diff --check` for doc edits; the project
  build + vet/lint gate (`../docs/conventions/audit-gate.md`) for
  code; focused module tests when business logic changed.

## Finding format

Each finding: **Severity · file:line evidence · Impact · Suggested
remediation · Verification status** (confirmed by local check vs
inferred from review).

| Severity | Bar |
|---|---|
| Critical | exploitable auth bypass, secret exposure, data corruption, RCE, broken critical pipeline |
| High | broken security control, stale authorization, data-integrity defect, production-breaking path |
| Medium | bounded data exposure, DoS vector, validation gap, defect-prone maintainability issue |
| Low | cleanup, doc drift, minor UX inconsistency, polish |

## Applying audit fixes — discipline (hard rule)

Automated "fix everything the audit found" passes reliably
OVERREACH: they bundle unrelated cleanups, touch files outside the
audited scope, and can violate standing charters (e.g. a
read-only-database rule for the session, or a "docs-only" mandate).
Before any audit-driven fix lands:

1. **Curate** the findings — fix only what you triaged as
   fix-now; the rest goes to the backlog with severity.
2. **Tightly scope** each fix to the finding's file(s); no
   opportunistic refactors riding along.
3. **Review the full diff before commit** — an auto-fix stage's
   output is a claim like any other agent output.
4. Fixes obey every standing charter and hard invariant; an audit
   finding never authorizes bypassing one.

## Artifacts and closing the loop

1. Formal report → `../history/audits/AUDIT-YYYY-MM-DD-<slug>.md`
   (durable, global — authorship in the header).
2. Short reply → the declared response path (`RESP-...md`), linking
   the report.
3. The requesting agent triages findings: fix now (commits reference
   the audit), or record in `../active/backlog.md` with severity.
   **Every Medium+ finding must land in one of those two places** —
   findings that live only inside reports get lost.
4. Remediation gets a `FOLLOWUP-` re-audit when severity warrants;
   the auditor re-inspects code, not claims.
5. Both message files move to `../history/inbox/` once resolved.
