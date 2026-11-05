# Frontend (frontend-v2)

The customer web app (Next.js 16): landing, auth, account dashboard, checkout. It consumes the
public `/api/v1/**` API.

## Where it lives

| What | Where |
|---|---|
| Repo on operator's laptop | `C:/Users/taras/Desktop/ct-projects/frontend-v2` (default branch `main`) |
| Prod deploy | push to `main` → GitHub Actions → `frontend` container on `ct-backend` (`/root/coursestools`). About 1 min of 502s on coursestools.com while it restarts |
| Runtime endpoint it calls | `NEXT_PUBLIC_API_URL` → `https://api.coursestools.com/api` |
| Its docs workspace | `frontend-v2/claude-docs/` (gitignored, local). Index `CLAUDE_README.md`; commit rules `CLAUDE_GIT.md` (no "Claude" or session talk in commits; `claude-git/` memo per change) |

## What they consume (contracts that bite)

- `GET /v1/users/me/subscriptions` (`UserSubscriptionReadDto`). Since 2026-11-05 it carries
  `gracePeriodEndsAt` (UTC, no zone) while `status = GRACE_PERIOD`. The dashboard derives the grace
  countdown and "expired" from it (`src/features/account/dashboard/model/grace-period.ts`) and falls
  back to `expiredAt + 7` days when it is absent. Contract: C13; DEC-006.
- During grace the dashboard says "alerts paused" (C18: grace pauses alerts, never deletes them).
- Types come from our OpenAPI schema (`bun run api` → `src/shared/api/schema/generated.ts`). A DTO
  change here breaks their build until they regenerate.

## Binding invariants on our side

- Never remove or rename `gracePeriodEndsAt` or the status values without a frontend task.
- Timestamps in customer DTOs stay UTC `LocalDateTime` (no offset). The frontend parses them with
  plain `dayjs(...)`.
- Marketing copy on their side states a 7-day grace policy (home FAQ, getting-ready page). If the
  admin-configured grace length changes, the operator must update that copy.

## Sending work to their agents

Drop a task file into `frontend-v2/claude-tasks/<TOPIC>.md` (gitignored, local). State the backend PR,
the contract, the exact files to change, and a status line their agent flips to done. Example:
`claude-tasks/GRACE-PERIOD-FROM-API.md` (shipped as frontend-v2#48).

## Change history

- 2026-11-05 — file created (gracePeriodEndsAt contract, PLAN-003; frontend-v2#48).
