# protocols/ — How Agents Coordinate

Conventions for work BETWEEN sessions and agents. Code/git/docs style
lives in `../docs/conventions/`.

| File | Subject | Read it when... |
|---|---|---|
| `roles.md` | Chief/TL, Worker, Auditor roles and who owns what | any multi-agent work starts |
| `subagents.md` | **In-session subagent development playbook** — decision gate, briefing, ownership, commit cadence, changelog | the operator says "use subagents" or the work parallelizes |
| `taskboard.md` | Cross-SESSION worker protocol (file-based task bus) | the operator opens parallel terminal sessions |
| `messaging.md` | Inter-agent + cross-project message format, inboxes, response paths | sending/receiving any inter-agent message |
| `audits.md` | Audit request/report format, severity ladder, zero-trust re-inspection | requesting or performing an audit |
| `new-agent.md` | Mandatory checklist for onboarding a new LLM agent (workspace, shim, registration) | adding any new agent |
| `lifecycle.md` | Session-close checklist; active→history archiving rules | ending any working session |
