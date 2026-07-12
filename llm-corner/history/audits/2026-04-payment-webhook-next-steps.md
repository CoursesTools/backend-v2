# Next Steps — Morning of 2026-04-11

**Read `01-INVESTIGATION-SUMMARY.md` first** if you're picking this up fresh. That file has the full context, timeline of theories explored, and all the data we've gathered so far.

**TL;DR of current state:** Stripe webhooks stopped being processed sometime between `2026-04-08 07:42:21 UTC` and `2026-04-09 14:54 UTC` (~31h window). Backend code is fine, endpoint is reachable and returns the correct 400 for unsigned POSTs, log pipeline is healthy. Need Stripe dashboard access to see the delivery attempt history and endpoint status.

---

## Priority 1 — As soon as Stripe access is granted (takes 5 minutes)

Log in to the Stripe dashboard and immediately screenshot the following pages so we have a record:

### 1A — The webhook endpoint

**Developers → Webhooks** — this is the answer to 95% of our open questions.

Look at:
- **Is there an endpoint listed?** If the list is empty, nothing has ever been sending. Unlikely given Stripe worked on Apr 8, but worth ruling out.
- **What is the Endpoint URL?** Copy it exactly. Compare against `https://api.coursestools.com/api/v1/payments/stripe`.
- **Endpoint status:** `Enabled` or `Disabled`?
  - If `Disabled`: **this is the root cause.** Click "Enable." Note the timestamp of when Stripe disabled it — that's the break moment.
  - If `Enabled`: the issue is different, see section 1C below.
- **Which events is it subscribed to?** Must include at minimum `invoice.payment_succeeded` and `checkout.session.completed`. Screenshot the full list.
- **Signing secret:** click "Reveal" and copy the first 12 characters. Compare against the backend's env var (see 1B below).

### 1B — Compare signing secret

On the NL backend host, print the currently-loaded webhook secret:
```bash
docker exec backend printenv STRIPE_WEBHOOK_SECRET | cut -c1-16
```
Compare the first 12 characters to the "Signing secret" shown in the Stripe dashboard. If they differ, **that is the root cause** — the backend's env var is stale because someone rotated the Stripe secret (or Stripe rotated it automatically) without deploying a new `.env`. Fix: update `.env` on the NL host, redeploy.

### 1C — Recent deliveries

**Developers → Webhooks → [your endpoint] → Event deliveries** (or "Recent attempts" tab).

This shows every delivery Stripe has tried in the last few days. Specifically for each attempt:
- Timestamp
- HTTP response code from our backend (200, 400, 502, "No response", "Connection timed out", "Endpoint disabled")
- Event type
- Event ID

**What we want to see (sorted newest first):**
1. Last attempt at `2026-04-08 07:42:21 UTC` or slightly after — should show a `200 OK`. If it's the last "green" one, this lines up with our Loki data and confirms the break happens immediately after.
2. Anything between `2026-04-08 07:43` and `2026-04-09 14:54` — this is the break window. These attempts are what we most need to see. They will tell us the failure mode: signature error (400), timeout (no response), connection refused, or endpoint-disabled.
3. Mukul Salaria's event for `pi_3TKi28GjHMWbNU7A1qR7bUV1` (paid `2026-04-09 14:54:xx UTC`). Find the corresponding `invoice.payment_succeeded` or `checkout.session.completed` event and look at its delivery attempts.
4. Anything in the last 24 hours — if Stripe is still attempting and getting failures, we need to see that.

**Screenshot each of these and attach them to a new session conversation. That alone will let us identify the cause in seconds.**

### 1D — Events log (alternative view)

**Developers → Events** — shows every Stripe event the account generated, regardless of webhook delivery. Filter by date range `2026-04-08 00:00 to now`. For each event, click it and look at the "Webhook attempts" section. This gives a different view of the same data and is sometimes clearer.

---

## Priority 2 — Backend/Loki diagnostics (can run in parallel, doesn't need Stripe access)

These queries narrow the break window further from within our logs. Run them on the RU observability host (77.232.135.132).

### NQ1 — Absolute latest Stripe event (large limit, no truncation risk)

```bash
START_NS=$(date -d '72 hours ago' +%s%N)
END_NS=$(date +%s%N)
curl -s -H "X-Scope-OrgID: backend" \
  -G --data-urlencode 'query={compose_service="backend", logger="com.winworld.coursestools.service.payment.impl.StripePaymentService"}' \
     --data-urlencode "start=$START_NS" \
     --data-urlencode "end=$END_NS" \
     --data-urlencode "limit=5000" \
     --data-urlencode "direction=BACKWARD" \
  "http://localhost:3100/loki/api/v1/query_range" \
  > /tmp/loki-stripe-72h.json

# Extract timestamps across all streams, sort, show newest 20
cat /tmp/loki-stripe-72h.json \
  | python3 -c '
import json, sys
d = json.load(sys.stdin)
entries = []
for stream in d.get("data", {}).get("result", []):
    for ts, msg in stream.get("values", []):
        entries.append((int(ts), msg))
entries.sort(reverse=True)
print(f"Total entries: {len(entries)}")
for ts, msg in entries[:20]:
    from datetime import datetime, timezone
    dt = datetime.fromtimestamp(ts / 1e9, tz=timezone.utc)
    short = msg.replace("\n", " ")[:140]
    print(f"{dt.isoformat()}  {short}")
'
```

**Confirms:** the absolute last `Processing Stripe webhook:` log line. If it's exactly `2026-04-08 07:42:21Z`, our analysis is correct. If there's something newer (e.g. April 9 or April 10), the break window shifts and we need to reconsider.

### NQ2 — All backend restarts / deploys in the last 72 hours

```bash
START_NS=$(date -d '72 hours ago' +%s%N)
END_NS=$(date +%s%N)
curl -s -H "X-Scope-OrgID: backend" \
  -G --data-urlencode 'query={compose_service="backend"} |~ "Starting CoursesToolsApplication|Started CoursesToolsApplication|Tomcat started on port"' \
     --data-urlencode "start=$START_NS" \
     --data-urlencode "end=$END_NS" \
     --data-urlencode "limit=200" \
     --data-urlencode "direction=FORWARD" \
  "http://localhost:3100/loki/api/v1/query_range" \
  | python3 -c '
import json, sys
d = json.load(sys.stdin)
entries = []
for stream in d.get("data", {}).get("result", []):
    for ts, msg in stream.get("values", []):
        entries.append((int(ts), msg))
entries.sort()
for ts, msg in entries:
    from datetime import datetime, timezone
    dt = datetime.fromtimestamp(ts / 1e9, tz=timezone.utc)
    short = msg.replace("\n", " ")[:140]
    print(f"{dt.isoformat()}  {short}")
'
```

**What to look for:** any backend startup in the break window. If there's a "Starting CoursesToolsApplication" at ~April 8 08:00 UTC, we have a trigger.

### NQ3 — Hourly Stripe event count for last 72 hours

```bash
START_NS=$(date -d '72 hours ago' +%s%N)
END_NS=$(date +%s%N)
curl -s -H "X-Scope-OrgID: backend" \
  -G --data-urlencode 'query=sum(count_over_time({compose_service="backend", logger="com.winworld.coursestools.service.payment.impl.StripePaymentService"}[1h]))' \
     --data-urlencode "start=$START_NS" \
     --data-urlencode "end=$END_NS" \
     --data-urlencode "step=3600" \
  "http://localhost:3100/loki/api/v1/query_range" \
  | python3 -c '
import json, sys
from datetime import datetime, timezone
d = json.load(sys.stdin)
for stream in d.get("data", {}).get("result", []):
    for ts, count in stream.get("values", []):
        dt = datetime.fromtimestamp(float(ts), tz=timezone.utc)
        print(f"{dt.isoformat()}  {count}")
'
```

**What we want:** hourly histogram. The first hour with count=0 after 2026-04-08 07:00 UTC is the break hour.

### NQ4 — Hourly ERROR count for last 72 hours

```bash
START_NS=$(date -d '72 hours ago' +%s%N)
END_NS=$(date +%s%N)
curl -s -H "X-Scope-OrgID: backend" \
  -G --data-urlencode 'query=sum(count_over_time({compose_service="backend", level="ERROR"}[1h]))' \
     --data-urlencode "start=$START_NS" \
     --data-urlencode "end=$END_NS" \
     --data-urlencode "step=3600" \
  "http://localhost:3100/loki/api/v1/query_range" \
  | python3 -c '
import json, sys
from datetime import datetime, timezone
d = json.load(sys.stdin)
for stream in d.get("data", {}).get("result", []):
    for ts, count in stream.get("values", []):
        dt = datetime.fromtimestamp(float(ts), tz=timezone.utc)
        print(f"{dt.isoformat()}  {count}")
'
```

**What we want:** any error spike around 2026-04-08 07:42 or 08:00 UTC.

### NQ5 — Any payment/security ERRORs in the break window

```bash
START_NS=$(date -d '2026-04-08 07:00:00 UTC' +%s%N)
END_NS=$(date -d '2026-04-10 00:00:00 UTC' +%s%N)
curl -s -H "X-Scope-OrgID: backend" \
  -G --data-urlencode 'query={compose_service="backend", level="ERROR"} |~ "(?i)(stripe|payment|signature|webhook|security)"' \
     --data-urlencode "start=$START_NS" \
     --data-urlencode "end=$END_NS" \
     --data-urlencode "limit=200" \
     --data-urlencode "direction=FORWARD" \
  "http://localhost:3100/loki/api/v1/query_range" \
  | python3 -c '
import json, sys
from datetime import datetime, timezone
d = json.load(sys.stdin)
entries = []
for stream in d.get("data", {}).get("result", []):
    logger = stream.get("stream", {}).get("logger", "")
    for ts, msg in stream.get("values", []):
        entries.append((int(ts), logger, msg))
entries.sort()
for ts, logger, msg in entries:
    dt = datetime.fromtimestamp(ts / 1e9, tz=timezone.utc)
    short = msg.replace("\n", " ")[:200]
    print(f"{dt.isoformat()}  [{logger}]  {short}")
'
```

**What to look for:** `SignatureVerificationException`, `SecurityException`, `PaymentProcessingException`, anything with "Stripe" in the message. If there's a cluster of these starting at ~04-08 07:42, that's the smoking gun for a signing secret issue.

### NQ6 — Full ERROR/WARN stream for April 8 alone

```bash
START_NS=$(date -d '2026-04-08 00:00:00 UTC' +%s%N)
END_NS=$(date -d '2026-04-09 00:00:00 UTC' +%s%N)
curl -s -H "X-Scope-OrgID: backend" \
  -G --data-urlencode 'query={compose_service="backend", level=~"ERROR|WARN"}' \
     --data-urlencode "start=$START_NS" \
     --data-urlencode "end=$END_NS" \
     --data-urlencode "limit=500" \
     --data-urlencode "direction=FORWARD" \
  "http://localhost:3100/loki/api/v1/query_range" \
  | python3 -c '
import json, sys
from datetime import datetime, timezone
d = json.load(sys.stdin)
entries = []
for stream in d.get("data", {}).get("result", []):
    logger = stream.get("stream", {}).get("logger", "?")
    level = stream.get("stream", {}).get("level", "?")
    for ts, msg in stream.get("values", []):
        entries.append((int(ts), level, logger, msg))
entries.sort()
for ts, level, logger, msg in entries:
    dt = datetime.fromtimestamp(ts / 1e9, tz=timezone.utc)
    short = msg.replace("\n", " ")[:200]
    print(f"{dt.isoformat()}  {level:5}  [{logger}]  {short}")
' > /tmp/loki-apr08-errors.txt
wc -l /tmp/loki-apr08-errors.txt
head -50 /tmp/loki-apr08-errors.txt
echo "..."
tail -50 /tmp/loki-apr08-errors.txt
```

**What to look for:** any new error pattern emerging in the afternoon/evening of April 8, after the last successful Stripe webhook at 07:42.

### NQ-Grafana — easier alternative to NQ1–NQ6

Open `https://grafana.winworldteam.com` in a browser, log in, go to Explore, pick the Loki datasource.

Add custom HTTP header on the datasource if needed: Connections → Data sources → Loki → HTTP headers → `X-Scope-OrgID: backend`.

Then run these queries with Time range = `Last 3 days`:

```logql
# The one chart that tells the whole story — Stripe webhook count by hour
sum(count_over_time({compose_service="backend", logger="com.winworld.coursestools.service.payment.impl.StripePaymentService"}[1h]))

# Backend error count by hour
sum(count_over_time({compose_service="backend", level="ERROR"}[1h]))

# Raw log stream from April 8 onward
{compose_service="backend"} |= "Stripe"

# ALL payment / security errors in the break window
{compose_service="backend", level="ERROR"} |~ "(?i)(stripe|payment|signature|webhook|security)"
```

Screenshot the histogram from the first query. **That alone answers "when exactly did it break."**

---

## Priority 3 — Database reconciliation (can run in parallel)

On the NL backend host, identify affected customers. This list is what you'll need to manually or automatically reprocess once the webhook pipe is restored.

```bash
docker exec -i postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB_NAME" <<'SQL'
\echo
\echo '=== Victims: Stripe orders since Apr 8 07:42 UTC still PENDING ==='
SELECT o.id AS order_id,
       o.status,
       (o.total_price::float / 100) AS price_usd,
       o.created_at AS order_created,
       u.email,
       u.id AS user_id,
       us.trading_view_name,
       sp.display_name AS plan,
       sp.tier,
       sp.duration_days
FROM orders o
JOIN users u ON u.id = o.user_id
LEFT JOIN user_socials us ON us.user_id = u.id
JOIN subscription_plans sp ON sp.id = o.plan_id
WHERE o.payment_method = 'STRIPE'
  AND o.created_at >= '2026-04-08 07:42:21+00'
  AND o.status = 'PENDING'
ORDER BY o.created_at;

\echo
\echo '=== Cross-check: Stripe order histogram since Mar 01 ==='
SELECT DATE(o.created_at) AS day,
       o.status,
       COUNT(*) AS orders,
       (SUM(o.total_price)::float / 100)::numeric(10,2) AS usd
FROM orders o
WHERE o.payment_method = 'STRIPE'
  AND o.created_at >= '2026-03-01'
GROUP BY 1, 2
ORDER BY day, status;

\echo
\echo '=== Subscriptions for victim users (to see if they have any active sub at all) ==='
SELECT u.id, u.email, us.status, us.is_trial, us.expired_at, sp.display_name, sp.tier
FROM users u
LEFT JOIN users_subscriptions us ON us.user_id = u.id AND us.status IN ('GRANTED', 'PENDING', 'GRACE_PERIOD')
LEFT JOIN subscription_plans sp ON sp.id = us.plan_id
WHERE u.id IN (
  SELECT DISTINCT o.user_id
  FROM orders o
  WHERE o.payment_method = 'STRIPE'
    AND o.created_at >= '2026-04-08 07:42:21+00'
    AND o.status = 'PENDING'
);
SQL
```

**What the output tells us:**
- First query: the list of paying customers you need to reconcile. Probably a small number (a dozen or so at most, given ~2 days of break and typical volume).
- Second query: cross-validation from the DB side. The break date will show up as "PENDING starts piling up, PAID drops to zero" on a specific date. **Expect the transition on April 8.** If it's different from April 8, we've learned something.
- Third query: for each victim, whether they already have any subscription row (trial, grace period, etc.). Useful to know before reconciling so we know whether to call `createNewSubscription` or `extendExistingSubscription`.

---

## Priority 4 — Decision tree based on what we find

### Scenario A — Stripe dashboard shows endpoint is "Disabled"

**Root cause:** auto-disabled due to consecutive failures.

**Fix:**
1. Click **Enable** on the endpoint in Stripe dashboard.
2. For each failed delivery in "Recent deliveries" (since the last successful one on Apr 8 07:42), click the event → click **Resend** (one by one, or select all and batch-resend).
3. As Stripe re-delivers, the backend's new diagnostic logging (added today in GlobalExceptionHandler fix) will confirm receipt. Monitor with:
   ```bash
   # On RU host
   curl -s -H "X-Scope-OrgID: backend" \
     -G --data-urlencode 'query={compose_service="backend", logger="com.winworld.coursestools.service.payment.impl.StripePaymentService"}' \
        --data-urlencode "start=$(date -d '10 minutes ago' +%s%N)" \
        --data-urlencode "end=$(date +%s%N)" \
     "http://localhost:3100/loki/api/v1/query_range" | python3 -m json.tool | head -40
   ```
4. Verify each victim's order transitions to PAID and they get a subscription by re-running the DB reconciliation query.
5. Investigate what caused the original failures (check NQ5/NQ6 output in case it was ephemeral or still ongoing).
6. **Consider adding monitoring alert:** a Loki alert that fires when `count_over_time({compose_service="backend", logger="com.winworld.coursestools.service.payment.impl.StripePaymentService"}[6h]) == 0` and it's business hours — catches the same class of silent failure in the future within hours instead of days.

### Scenario B — Endpoint is Enabled but Recent Deliveries all failing with HTTP 400

**Root cause:** signature verification is failing.

**Most likely:** `STRIPE_WEBHOOK_SECRET` env var on the backend is stale (doesn't match the dashboard's Signing secret). Or Stripe auto-rotated the secret.

**Fix:**
1. Compare env var to dashboard secret (step 1B above). Confirm they differ.
2. Update `.env` on NL host with the correct signing secret.
3. Restart backend: `docker compose -f /root/coursestools/docker-compose.yml restart backend` (or trigger a full redeploy).
4. Go back to Stripe dashboard → resend all failed events.
5. Monitor and reconcile as in Scenario A.

### Scenario C — Endpoint is Enabled but Recent Deliveries show "No response" / timeouts

**Root cause:** network layer between Stripe and our Caddy. Could be TLS handshake issue, firewall, or ISP-level routing.

**Investigate:**
1. Check Caddy's access log for ANY inbound connections from Stripe's webhook source IPs (AWS us-east-1/us-west-2 ranges — they publish the list at https://stripe.com/files/ips/ips_webhooks.txt). Specifically:
   ```bash
   # On NL host, inside Caddy container's log file
   CADDY_LOG=/var/lib/docker/containers/$(docker inspect caddy --format '{{.Id}}')/$(docker inspect caddy --format '{{.Id}}')-json.log
   grep -E '"remote_ip":"(3\.|13\.|18\.|34\.|35\.|52\.|54\.)' $CADDY_LOG | head
   ```
2. Check iptables/nftables on NL host for anything new.
3. Try `curl` to our endpoint from a different external location (different ASN) to see if specific networks are being blocked.
4. Contact Timeweb support about any network filtering.

**Fallback fix:** set up a tiny webhook relay VPS in Hetzner/AWS, point Stripe at that, have it forward to us. Worst-case bypass.

### Scenario D — Endpoint is Enabled but Stripe dashboard shows successful deliveries that we don't see in Loki

**Root cause:** something between Caddy and the backend is silently dropping requests. Or the log pipeline from backend to Loki has a gap for specific conditions.

**Investigate:**
1. Check Caddy's access log for POST /api/v1/payments/stripe — should match what Stripe dashboard shows.
2. If Caddy has the requests but backend doesn't log processing them: check backend's Spring Security filter chain, check if JWT filter is (incorrectly) intercepting the webhook despite `PublicUrlsHolder`.
3. If Caddy also doesn't have them: the issue is between Stripe and Caddy — see Scenario C.

### Scenario E — Nothing obvious, endpoint looks healthy in dashboard and deliveries look like they're succeeding

**Root cause:** mismatch between our DB state and reality. Orders are marked PENDING but actually got processed in some other way.

**Investigate:**
1. Pick one victim order from the reconciliation list.
2. Look up the corresponding Stripe Customer and payment intent in Stripe dashboard.
3. Check if the customer has an active subscription in Stripe.
4. If Stripe says active but our DB says PENDING — there's a processing bug downstream of `Webhook.constructEvent`, likely in `OrderService.processSuccessfulPayment` or the async `SubscriptionChangeStatusListener`. Look for errors around those loggers in the break window.
5. This would be a real code bug. Need to debug the Java path.

---

## Priority 5 — Reconciliation plan (once webhook pipeline is restored)

For each affected customer identified in Priority 3:

### Option A (preferred) — Resend the events from Stripe dashboard
1. In Stripe → Developers → Webhooks → [endpoint] → Event deliveries
2. Filter by date range: from 2026-04-08 07:43 UTC to now
3. For each failed event, click "Resend". Stripe will redeliver to our endpoint; the backend will process normally (assuming the pipe is now healthy).
4. Watch Loki in real-time to confirm each event is processed:
   ```bash
   # On RU host, tail the Stripe stream
   watch -n 5 'curl -s -H "X-Scope-OrgID: backend" \
     -G --data-urlencode "query={compose_service=\"backend\", logger=\"com.winworld.coursestools.service.payment.impl.StripePaymentService\"}" \
        --data-urlencode "start=$(date -d "10 minutes ago" +%s%N)" \
        --data-urlencode "end=$(date +%s%N)" \
     "http://localhost:3100/loki/api/v1/query_range" \
     | python3 -c "import json, sys; d=json.load(sys.stdin); print(sum(len(s[\"values\"]) for s in d[\"data\"][\"result\"]))"'
   ```
5. After resending all, re-run the DB reconciliation query. The PENDING list should shrink to 0.

### Option B (fallback) — Admin endpoint
If resending from Stripe doesn't work (e.g., events aged out of Stripe's retention — ~30 days typically), use the admin endpoint `AdminController.changeUserAccess()` to manually grant each victim their purchased tier. Requires the admin JWT and the user's tradingView username. Alternatively, construct a one-off script that:
1. Looks up the Stripe customer for the order
2. Fetches the subscription details from Stripe API
3. Manually calls `SubscriptionService.createNewSubscription` with the correct plan and expiration

---

## If I'm wrong about everything

In case my entire theory crumbles based on what you find in the Stripe dashboard, here's the minimum-viable next step: **paste the Stripe dashboard screenshots and NQ1 output into a new Claude conversation with `01-INVESTIGATION-SUMMARY.md` attached.** I'll pick up from exactly where we stopped and move forward based on whatever the real ground truth turns out to be.

The investigation context files are:
- `claude-payment-issue/01-INVESTIGATION-SUMMARY.md` — the complete story so far
- `claude-payment-issue/02-NEXT-STEPS.md` — this file
- `claude-git/MSG-FIX-CRYPTOCLOUD-WEBHOOK.md` — CryptoCloud PR message (already deployed)
- `claude-docs/CLAUDE_README.md` — project overview

And the relevant code that's deployed:
- Branch: `fix/cryptocloud-workflow-rebuild` (not yet merged to master)
- All CryptoCloud hardening is in
- Global exception handler fix for client-disconnect noise is in
- Nothing in the Stripe processing path has been modified today

Good night. Rest up. We'll nail this one tomorrow.
