# Git Conventions

## Commit message format

```
<type>(<scope>)?: <short subject ≤ 70 chars>

<body — wrap at 72 chars; explain WHY, not just WHAT>

Co-Authored-By: <agent attribution — see below>
```

`<type>` is one of the types observed throughout this repo's history:
`feat`, `fix`, `chore`, `refactor` (add `docs`, `test` as needed).
`<scope>` is optional and names the affected surface — observed:
`feat(admin): …`, `feat(admin-web): …`, `fix(review): …`.

The body MUST explain why the change exists — the operator's PR review
reads this, not the diff alone. Reference model: commit `ea8ec83`
("fix: +1 day TradingView bot access buffer + failure diagnostics"),
whose body states the user-visible symptom, the root cause, each change
with its reason, and the tests.

## Agent attribution

- Claude commits end with two trailers:
  `Co-Authored-By: Claude <model name> <noreply@anthropic.com>` and
  `Claude-Session: https://claude.ai/code/session_…` (observed on
  `ea8ec83`).
- Operator-only commits omit the trailers.
- **Merge commits into master are retitled by the operator to credit
  the implementing agent:**
  `Fix (<Agent>): <subject>` or `Feature (<Agent>) [<scope>]: <subject>`
  — observed: `Fix (Codex): sync Stripe lifecycle expiry changes to
  TradingView` (`af1eaf4`), `Feature (Claude Code) [admin]: added
  partnerCode param …` (`49b2a2d`). GitHub-merged PRs also appear as
  plain `Merge pull request #NN from CoursesTools/<branch>` or a
  squash subject suffixed `(#NN)`.

When the work was implemented by a subagent under a chief agent, the
chief commits and credits the implementer in the body:
`Implemented-By: <subagent label>`. The `Co-Authored-By` trailer stays
the chief's model. See `../../protocols/subagents.md`.

## Atomic commits

One feature = one commit. Each commit includes its doc update
(changelog entry; architecture doc if a contract changed).

Multi-file changes are fine; mixed-feature changes are not. If you
caught yourself fixing a typo while building a feature, two commits.

Further hard rules:

- **Security changes get their own commits** — never bundled with
  features.
- **Flyway migrations are separate commits** from the code that uses
  them (see `code.md`, SQL / migrations).
- **Explicit pathspec on every git command.** Never `git add -A` or
  `git add .`. Always `git add ./path/file` then
  `git commit -- ./path/file ./other/file`. The repo root carries
  scratch files (`tmp_alerts_insert.sql`, `TRANSP_FOLDER`, doc corners)
  that must never ride along.
- **Pre-commit safety check.** Run `git diff --cached --name-only`
  before committing. Abort if any file you don't own appears; use
  `git restore --staged <file>` to unstage.
- **No secrets in commits.** `.env` holds live Stripe keys and VPS
  passwords — it must never be staged.

## Do not commit or push automatically

When work creates committable changes, stop after verification and
ask the operator — unless the operator explicitly requested
commit/push in the current task. (Hard invariant — `mindset.md`.)

## Push safety (push to master = prod deploy)

**ANY push to `master` auto-deploys production.**
`.github/workflows/docker-build.yml` triggers on
`push: branches: [master]` (lines 3–5), runs `./gradlew build`
(line 34), pushes `ghcr.io/coursestools/backend:latest` (line 51),
then SSHes to the backend VPS and runs
`docker compose pull backend && docker compose up -d backend` in
`/root/coursestools` (lines 67–72). There is no staging environment
in this pipeline.

Consequences — all hard rules:

- **Never push directly to master.** Branch off master
  (`feat/…`, `fix/…`, `chore/…` — observed naming), open a PR,
  and let the operator merge. Merging IS deploying: treat the merge
  button as a deploy button.
- **Never merge without the operator's go-ahead**, even an approved
  PR — the operator chooses the deploy moment.
- **Force-push is forbidden on master** and on any branch with an
  open reviewed PR.
- `--no-verify` / bypassing checks is forbidden; if something fails,
  fix the cause.
- The workflow also supports manual `workflow_dispatch` — same
  caution applies: dispatching it deploys whatever master holds.

## PR flow

- PR from the feature branch into `master`.
- PR body uses the observed structure: **Summary** (bullet list of
  changes), **Why** (the incident/reason), **Test plan** (checklist
  incl. `./gradlew test` and prod verification steps) — see the PR
  entries in `../../history/changelog/` for the historical shape
  (the original PR-text drafts were not migrated).
- Review happens before merge; review fixes land as additional
  commits on the branch (observed on PR #33: `fix(review): …`,
  `refactor(review-nits): …`).

## Pre-commit gate

Always run before staging:

```sh
./gradlew build     # compiles + runs all tests (JUnit 5)
```

There is no separate linter/formatter plugin in `build.gradle.kts` —
the build IS the gate. CI runs the identical `./gradlew build` before
the image is built (docker-build.yml:34), so a red local build is a
guaranteed red deploy. For a faster loop while iterating:
`./gradlew test --tests "ClassName"`.
See `audit-gate.md` for the full pre-commit / pre-push checklist.
