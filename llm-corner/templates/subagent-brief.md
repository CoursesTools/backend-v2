# Subagent Brief — <unit name>

(Paste as the Agent-tool prompt. The subagent has ZERO conversation
context — this brief is everything it knows. Protocol:
`../protocols/subagents.md`.)

You are a subagent on the <<PROJECT_NAME>> project, working dir
`<<PROJECT_PATH>>`.

GOAL: <one sentence>.

OWNED FILES (create/modify ONLY these):
- <path> — <what to do there, line numbers if known>

READ-ONLY (read before writing code):
- <path> — <what to take from it>

FORBIDDEN: everything else. If the task seems to require touching a
non-owned file, STOP and report the needed change instead.

CONVENTIONS:
- llm-corner/docs/conventions/code.md (<+ Frontend section for UI work>)
- <relevant architecture doc / gotchas.md entries>

VERIFY: `<<BUILD_GATE_COMMAND>>` <+ specific tests/render checks>.
A failure outside your packages/files is not yours to fix — note it.

DO NOT commit, stage, or run any git mutation.

REPORT BACK (your final message, ≤300 words):
1. Files created/modified
2. Gate status (build/vet/lint)
3. Shared-file requests (exact change per file)
4. Decisions made / edge cases found
5. Suggested changelog line: `[domain][<your label>] <one line>`
