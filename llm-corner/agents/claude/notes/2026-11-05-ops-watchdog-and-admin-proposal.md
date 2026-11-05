# Proposal — Ops watchdog (Telegram) + admin self-serve (2026-11-05)

Status: IMPLEMENTED as PLAN-003 (released 2026-11-05, `history/plans/PLAN-003-*.md`). Ideas not
picked remain here for reference only. The current state is in `docs/architecture/ops-alerts.md`.

## Why

Every incident from Oct to Nov 2026 was found by customers or by an agent
manually reading logs and Stripe, days late:

- `winworldteam.com` expired: webhooks and logs were dead for 5 days.
- Unpaid renewals got a free month: running since May.
- Alerts are lost daily to Telegram blocking.
- A churned customer's cancellation comment ("Cant use the indicator even
  tho i paid") sat in Stripe unread.

Goal: the operator hears about money/access/alert problems within minutes
on Telegram, and can act from admin-web without an agent.

## Design principles

- **Checks live in the backend.** It is the only service on a non-RU host
  (Amsterdam), so it can reach Telegram reliably; the alert host and the
  Grafana host are both in Russia. Each check is a `@Scheduled` job that
  writes its result to an `ops_check_results` table.
- **Notifications** go to the operator's chat through a *separate ops bot*
  (new BotFather token, env `OPS_TELEGRAM_BOT_TOKEN` / `OPS_TELEGRAM_CHAT_ID`),
  never the customer alert bot:
  - edge-triggered: FAIL sends a message, OK sends "recovered";
  - repeats every N hours while still failing;
  - every message carries a deep link into admin-web.
- **The same table backs an admin "System health" page**, so Telegram and the
  UI never disagree.
- **Every admin action is audited.** A new `admin_actions` table records
  actor, target, payload and reason.

## Checks, by priority

| # | Check | How | Fires when | Sev | Would have caught |
|---|---|---|---|---|---|
| 1 | Stripe webhook delivery | Stripe API: our 4 event types, `pending_webhooks>0`, older than 15 min; endpoint `status`; endpoint host resolves to prod | any | Crit | Oct domain outage (in 15 min, not 5 days) |
| 2 | Paid but not processed | Stripe invoices paid in last 48h vs local `invoiceId` / order PAID | missing after 15 min | Crit | Oct victims |
| 3 | Access without payment | Stripe-backed GRANTED subs whose latest invoice is open past the settlement window, or local expiry > last paid period end | any | High | the May→Nov free-month leak (DEC-005 regression guard) |
| 4 | Disputes / refunds | Stripe `charge.dispute.created`, `charge.refunded` | any | Crit | — |
| 5 | TV retry queue | DEAD jobs > 0; PENDING older than 30 min | any | High | Oct DEAD rows |
| 6 | Alert pipeline | alert-bot `/api/ops/stats` (new, secret-protected): received, sent, failed, requeued, queue size, Telegram failure %, last send | Telegram fail > 5%/15 min, queue growing, no alert received 30 min in market hours | Crit | Nov Telegram losses |
| 7 | Domain expiry | RDAP for coursestools.com, winworldteam.com, winworldteam.ru, tw1.su | 30/14/7/1 days | Crit at 7 | Oct outage (coursestools.com expires **12-19**) |
| 8 | TLS expiry | every public hostname | 14 days | High | — |
| 9 | Log freshness | newest Loki line for `backend` | older than 15 min | High | Oct 5-day blind spot |
| 10 | CryptoCloud reconciliation | PENDING crypto orders vs CryptoCloud invoice status API | paid but PENDING | High | Apr postback redirect |
| 11 | Alert catalog mismatch | daily: "Alert not found" by (indicator, event) vs `users_alerts` subscriptions to the same event under another indicator | any subscribed event never matches | Med | possible `crossing_equlibrium` / `EQH` / `EQL` gap |
| 12 | Unreachable Telegram users | alert-bot records `chat not found` / `blocked` per chat | daily list | Med | ~144 failed sends/day |

**Daily business digest (09:00 MSK):**

- new trials, trial→paid conversion;
- renewals paid / failed / awaiting customer authentication;
- revenue;
- cancellations **with Stripe cancellation comments**;
- open DEAD jobs;
- the alert delivery rate.

## Admin-web: what to add (grounded in today's inventory)

1. **System health page** (new). One tile per check (green/amber/red), last
   run, details, "run now", ack/mute, incident history.
2. **Payments console** (new):
   - undelivered Stripe events with a **Resend** button (backend calls
     Stripe `events/{id}/retry`, one at a time, verifying it was processed);
   - paid-but-unprocessed invoices with an idempotent **Reconcile** button;
   - a dunning board: open renewals, attempts, next retry, needs-auth;
   - CryptoCloud pending reconciliation.

   This makes what the agent did by hand on 10-25 a one-click operator
   action.
3. **User 360** (upgrade `/users`):
   - search by telegram id or partial email, with a result list;
   - subscription card with Stripe state (status, latest invoice, period,
     cancel flag + **cancellation comment**, link to the Stripe dashboard);
   - orders and payments;
   - **TV command history**, needing a `tv_activation_log` of every bot
     command with payload, response and actor;
   - alert subscriptions and Telegram reachability;
   - trial history;
   - this user's admin audit trail.

   Today there is no subscription history at all.
4. **TV retry page guardrails**:
   - show the subscription's status, expiry and Stripe status next to each
     job;
   - **block force-retry on TERMINATED or unpaid subs** unless overridden
     with a typed reason;
   - link to the user;
   - filter by TV name and email.

   This prevents a repeat of job 487.
5. **Alerts admin** (the sidebar item already exists but is disabled):
   - pipeline health;
   - per-user subscriptions and last delivered alert;
   - "send test alert";
   - catalog editor and mismatch report;
   - users who must press /start.
6. **Safe data correction for Stripe-managed subs**: "Correct subscription"
   with preview diff, reason and audit. It replaces agent-written data-fix
   migrations like V16. Today `ensureNotStripeManaged` blocks every manual
   edit.
7. **Hygiene**:
   - a shared AlertDialog confirm for every impactful action (none exists;
     Classic "keep expiration" silently cancels Stripe);
   - cross-links between pages;
   - CSV export;
   - fix the Dashboard's stats fetching (currently `useMutation('get')`,
     so no caching).

## Suggested rollout (wall-clock with parallel agents)

- **P0 (1–2 days):**
  - ops bot plus notifier and `ops_check_results`;
  - checks 1, 2, 4, 5, 7, 8, 9;
  - alert-bot stats endpoint plus check 6;
  - the daily digest.
- **P1 (2–3 days):**
  - System health page;
  - Payments console (Resend / Reconcile / dunning);
  - TV-retry guardrails;
  - `admin_actions` audit log and confirm dialogs.
- **P2:** User 360 with `tv_activation_log`; Alerts admin; catalog mismatch
  check; safe correction tool.

## Operator decisions needed

- The ops bot token and target chat id.
- Business-digest content and timing.
- Alert thresholds (market hours for the "no alerts received" heartbeat).
