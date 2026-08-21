# 2026-08-21 — TradingView whole-second expiration hardening

Author: Codex (chief auditor / operator-directed incident recovery)
Commit: `96b6012`
PR: https://github.com/CoursesTools/backend-v2/pull/40

## State of the world

PR #40 carries DTO-boundary whole-second normalization for every TradingView
activation and rename command. Database timestamps, entitlement arithmetic,
the payment-only +1 day rule, and lifetime behavior are unchanged. Legacy
fractional retry JSON remains readable and normalizes before replay.

A controlled isolated `/open` request for `divega4864` used a whole-second
expiration, returned HTTP 200, and the operator confirmed real TradingView
access. The access server response was `Содержимое сохранено в open.txt`; that
only proves a shared-file write and explains why a concurrent startup recovery
burst can return 2xx while losing commands. This external High finding is in
the backlog.

## Shipped

- Activation and rename DTO setters normalize expiration to seconds after the
  exact/payment policy is applied (DEC-004, C17).
- Legacy fractional ACTIVATE and RENAME retry payloads deserialize safely and
  replay at whole-second precision; no DB migration is required.
- Regression coverage pins exact trial, paid-buffer ordering, rename, and the
  actual retry scheduler POST path.
- Authoritative architecture, ecosystem, gotcha, plan, contract, decision, and
  changelog docs reflect the new boundary and the current remote limitation.

## Production recovery

- `Priyankavardan29`: operator manually extended and confirmed access.
- `divega4864`: isolated whole-second recovery confirmed by the operator.
- `sunilbhati856678` (user 5246): isolated whole-second command returned HTTP
  200; operator confirmation is still required before sending the next user.
- Remaining sequence after confirmation: `tvinay2mmzt` (5247), `jitpal` (5248),
  `ffwefewf` (5249), `ossmantradrr` (5250), `usdxxau` (5252), `abujana` (5253),
  `Sagargupta` (5254). Send exactly one command, verify actual TradingView
  access with the operator, then proceed to the next.

## Open / deferred

- Do not interpret HTTP 2xx as applied access while the remote service uses a
  shared `open.txt`. The bot owner is responsible for durable per-command
  processing and an explicit outcome response.
- Do not enforce the proposed response DTO in backend until the bot owner
  confirms its deployed contract. Current backend intentionally uses
  `Void.class` for activation/rename responses.
- The timestamp remains offset-less. Explicit timezone/epoch rollout and any
  reduction of the payment safety day remain PLAN-001 work requiring bot-owner
  coordination.

## Verification record

Fresh `gradlew.bat build --rerun-tasks` passed: 106 tests, zero failures,
errors, or skips. Focused DTO/retry tests also passed. Diff whitespace, stale
contract wording, TODO/debug, and secret audits found no new issue; six
pre-existing compiler warnings remain unchanged.
