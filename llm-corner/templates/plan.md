# PLAN-NNN — <title>

**Status:** DRAFT | ACTIVE | SHIPPED YYYY-MM-DD | SUPERSEDED by PLAN-MMM
**Owner:** <chief agent>
**Commit cadence:** <e.g. "one commit per phase, operator pre-approved"
or "ask before each commit"> — agreed with the operator up front.

## 0. TL;DR

<2-4 sentences: the change, the why, the shape of the work.>

## 1. Context

<What exists now, what's wrong/missing. Link architecture docs and
DECs instead of restating them.>

## 2. Design

<The decision-shape work, settled BEFORE implementation. If a choice
here is significant, it gets a DEC file and a link.>

## 3. Phases

### Phase A — <name>
- [ ] <task — specific enough that a cold subagent could take it>
- Files: <owned paths>
- Verify: <how>

### Phase B — ...

## 4. Subagent split (if parallelizing)

<OWNED/READ-ONLY/FORBIDDEN per unit, per protocols/subagents.md — or
"solo session" and why.>

## 5. Risks / rollback

<What could go wrong; how a phase reverts (one feature = one commit
makes this cheap).>

## 6. Doc impact

<Which evergreen docs/contracts this will touch — updated in the same
commits, per C7.>

---
On completion: set final Status, run the session-close checklist
(`../protocols/lifecycle.md`) — archive to `../history/plans/` + INDEX
row; deferred items → `../active/backlog.md`.
