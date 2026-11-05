# DEC-006 — Grace pauses alerts; grace length is an admin setting

Status: ACCEPTED
Date: 2026-11-05
Author: Claude Code (operator decisions in the PLAN-003 session)

## Context

Until now the hourly expiry job deleted a user's `users_alerts` rows the
moment a subscription entered `GRACE_PERIOD`. A customer who renewed during
grace got TradingView access back, but every alert subscription was gone and
had to be rebuilt by hand. Alerts are the core product, so the operator wants
them to survive a late payment.

The grace length was the constant `GRACE_PERIOD_DAYS = 7`. Changing it meant
a code release.

## Options considered

1. Keep deleting at grace start and restore from a snapshot on payment.
   Rejected: a second copy of the subscription list, with drift risk.
2. Keep the rows and stop delivery in the backend. Rejected: the backend
   does not deliver. The alert bot reads `users_alerts` live from the shared
   DB, so a backend-side flag would need a new column the bot must read
   anyway.
3. **CHOSEN: keep the rows, and have the alert bot deliver only to users
   whose CoursesTools subscription is `GRANTED` or `PENDING`.** Delete the
   rows only when the subscription ends for good.

## Decision

- **Pause.** The alert bot's subscriber query joins `users_subscriptions`
  and requires `status IN ('GRANTED','PENDING')` for the COURSESTOOLS type
  (alert-bot-v2 `feat/alert-security-and-stats`). `GRACE_PERIOD` and
  `TERMINATED` users get nothing. A payment during grace moves the sub back
  to `PENDING`/`GRANTED`, so delivery resumes with the same list.
- **Delete.** `AlertService.removeAlertsIfNoActiveSubscription(userId)` runs
  after:
  - past-grace termination (hourly job and startup reconciler);
  - trial end;
  - Stripe subscription deletion.

  It deletes nothing if the user still has another current subscription.
  Entering grace no longer touches alerts.
- **Configurable grace.** `system_settings.grace_period_days` (1–30, DB
  CHECK), edited on the admin-web Settings page.
  `SystemSettingsService.getGracePeriodDays()` never throws: any read
  failure, missing row or out-of-range value returns **7**.
- The customer API exposes `gracePeriodEndsAt` on GRACE_PERIOD
  subscriptions, so the frontend stops hardcoding 7 days.

## Consequences

- Contract C13 is reworded and contract C18 is added.
- The bot and the backend now share one more cross-project invariant: the
  meaning of `users_subscriptions.status` for delivery
  (`../docs/ecosystem/alert-bot.md`).
- Shortening grace in the UI terminates every subscription already past the
  new cutoff on the next hourly run, and deletes their alert lists. The
  Settings page asks for confirmation. Lengthening it never revives
  subscriptions that are already terminated.
- Known gap: the read-time discard (`UserSubscriptionService` →
  `discardPastGracePeriodSubscription`) can terminate a sub in the window
  before the hourly job and does not clear the list. The bot filter already
  stops delivery. The rows stay inert until the user pays again, and then
  they resume.
