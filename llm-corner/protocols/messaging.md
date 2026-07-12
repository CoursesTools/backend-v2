# Messaging — Inter-Agent and Cross-Project

The single source of truth for agent-to-agent messages. (Supersedes
the four pre-llm-corner messaging docs — DEC-001.)

## Inboxes — one per agent, structurally separated

Every agent has exactly one inbox inside its workspace:

| Agent | Inbox |
|---|---|
| claude | `../agents/claude/inbox/` |
| codex | `../agents/codex/inbox/` |
| *(new agent)* | created per `new-agent.md` |

A message is a file in the **recipient's** inbox — always. That makes
the request/reply separation structural: agent A's request to agent B
lives in `agents/<b>/inbox/`; B's reply lives in `agents/<a>/inbox/`
(the declared response path). Requests and their replies never share a
directory. There is no outbox — outgoing messages go straight to the
recipient's inbox. Nobody writes into their own inbox.

When the operator says **"check inbox"**: check your own
`../agents/<you>/inbox/` AND scan the other agents' inboxes for
requests still waiting on them — report both sides.

## Message format

Filename: `<PREFIX->YYYY-MM-DD-<slug>.md`. Required header:

```md
# <PREFIX>-YYYY-MM-DD — <short title>
Created: YYYY-MM-DD
From: <agent>
To: <agent>
Response path: llm-corner/agents/<sender>/inbox/
Context: <plan / commit range / source request if relevant>
```

| Prefix | Meaning | Expected response |
|---|---|---|
| `AUDIT-REQ-` | request an audit (`audits.md` for scope rules) | `RESP-` + formal report in `../history/audits/` |
| `RESP-` | reply to a prior message (slug matches the original) | none |
| `FOLLOWUP-` | re-check after remediation | `RESP-` |
| `MSG-` | anything else durable | as declared |
| *(no prefix)* | operator notice to an agent | as the notice says |

## Rules

1. **Every message declares a `Response path:`** — respond exactly
   there. If a received message lacks one, default to the sender's inbox.
2. **Don't move/rename/delete another agent's inbox files** outside an
   operator-requested triage or the session-close sweep (`lifecycle.md`).
3. **Resolved messages move to `../history/inbox/`** (same filename) —
   the inbox shows only what's awaiting action. An empty inbox is the
   healthy state.
4. Long-lived artifacts referenced by a message (audit reports, plans)
   live in their own homes (`../history/audits/`, `../active/plans/`);
   the message links, never copies.

## Cross-project messages (e.g. the `admin-web` repo)

Same format; the file goes into the OTHER repo's inbox directory as
listed in `../docs/ecosystem/<project>.md`, with `Response path:`
pointing back to your inbox here (use a repo-qualified path, e.g.
`ct-projects/backend-v2/llm-corner/agents/<you>/inbox/`). Spell out cross-repo
contracts explicitly — the other repo's agents don't read our docs
unless you link exact files.
