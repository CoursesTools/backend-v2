# Claude Operating Mode

Claude has no exception to the project protocols. This file records how the
existing rules apply to Claude so creation of the personal corner cannot be
mistaken for a change in behavior.

## Chief responsibilities

- Follow `../../protocols/roles.md` as chief: own the plan, delegate bounded
  work, protect shared files, inspect actual diffs, integrate, verify, commit,
  document, and close the session cleanly.
- Follow the full boot and close sequences in `../../README.md` and
  `../../protocols/lifecycle.md`. Read this corner's inbox on boot and sweep it
  at close.
- Treat the backend as production at all times. The mindset and hard
  invariants in `../../docs/conventions/mindset.md` apply without exception.

## Git and delivery

- Follow `../../docs/conventions/git.md` exactly: gate before work and before
  every commit, use atomic conventional commits with an explanatory body,
  explicit pathspecs only, inspect the staged file list, and include Claude's
  required attribution and session trailers.
- Never commit or push without current-task operator authorization. Never push
  directly to `master`; a merge to `master` is a production deployment and
  requires explicit operator approval.
- Follow `../../docs/conventions/dev-flow.md` and
  `../../docs/conventions/audit-gate.md`; a green build alone does not prove an
  unwired external side effect works.

## Code, review, and documentation

- Apply `../../docs/conventions/code.md`, preserve the Controller -> Facade ->
  Service -> Repository boundaries, and update tests and contract docs with
  behavior changes.
- Evidence from another agent is input, not proof. Re-open files, inspect the
  diff, and run the full gate before integration.
- Keep project facts in the global llm-corner tree, never in personal notes.
  Follow `../../docs/conventions/docs-style.md` and record each commit in the
  changelog.

## Messaging

- `inbox/` is Claude's only inbound channel. Outbound messages go directly to
  the recipient's inbox; there is no outbox.
- Follow `../../protocols/messaging.md` for headers, response paths, cross-repo
  messages, replies, and archival. Never write an outbound message into this
  corner's own inbox.
