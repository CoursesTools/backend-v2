# Ecosystem

One file per connected project — what we consume, the contract
pointer, where it runs, how to message its agents.

## Connected projects

| Project | File | Relationship |
|---|---|---|
| TV access bot (grants TradingView indicator access) | `tv-access-bot.md` | we consume (fire-and-forget commands) |
| Alert bot (`alert-bot-v2`, Telegram alert delivery) | `alert-bot.md` | we consume (one notification endpoint) |
| Payment gateways (Stripe live, CryptoCloud; Payeer retired) | `payment-gateways.md` | two-way (we call their APIs, they call our webhooks) |
| Admin web (`admin-web`, React admin panel) | `admin-web.md` | they consume (our `/api/v1/admin` API) |

Adjacent projects in `C:/Users/taras/Desktop/ct-projects/` that this backend
does **not** call directly and that have no ecosystem file yet: `frontend-v2`
(main web client, consumes the whole public API), `alert-router` (routes
TradingView alert webhooks to Telegram bots; the routing contract belongs
to that project), `helper-tg-bot`. Add a file
here when a direct dependency appears.

## File template per ecosystem entry

```markdown
# <<EXTERNAL_PROJECT_NAME>>

<<ONE_LINE_DESCRIPTION>>

## Where it lives

| What | Where |
|---|---|
| Repo on operator's laptop | `<<local-path>>` |
| Prod deploy | `<<host + path>>` |
| Runtime endpoint (from inside this app) | `<<URL>>` |
| Its docs workspace | `<<path-to-its-llm-corner>>` |

## What we consume

- `<<endpoint or shape>>` — <<what it does for us>>.
- Contract docs (authoritative, in their repo):
  `<<path-to-their-contract-doc>>`.

## Binding invariants on our side

- <<...things that must hold for the integration to work...>>

## Sending work to their agents

Their inbox path: `<<external-project>>/<<inbox-path>>`.

Format: see `../../protocols/messaging.md` "Cross-project messages"
section. Declare `Response path: <<this-project-root>>/llm-corner/agents/<your-agent>/inbox/`.

## Change history

- YYYY-MM-DD — file created.
```

## Rules

- When a cross-project contract changes, the change lands in BOTH
  repos' docs in the same arc, and the ecosystem file here gets the
  new contract pointer — an ecosystem file that lies about a contract
  is worse than none.
- Cross-project agent messages follow `../../protocols/messaging.md`
  conventions (dated file, From/To header, declared `Response path:`),
  dropped into the **other repo's** inbox directory listed in its
  ecosystem file.
- New connected project (separate repo, API dependency, shared infra)
  ⇒ new file here in the same commit that introduces the dependency.
- All external base URLs come from `.env` and flow through the `urls:`
  block in `src/main/resources/application.yml:78-85` — an ecosystem
  file must quote the env var name, never hardcode a URL as the only
  source.

## Why ONE file per connected project (not one shared "integrations.md")

When an agent is debugging "why isn't X responding correctly", they
need to grep ONE file with everything about X. Sharded
integrations.md fragments invariably miss the relevant line. The
2-hop rule: ecosystem/ → <project>.md → answer.
