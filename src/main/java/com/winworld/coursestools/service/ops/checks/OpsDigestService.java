package com.winworld.coursestools.service.ops.checks;

import com.fasterxml.jackson.databind.JsonNode;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
import com.stripe.param.EventListParams;
import com.stripe.param.InvoiceListParams;
import com.winworld.coursestools.enums.OpsAlertCategory;
import com.winworld.coursestools.enums.OpsAlertSeverity;
import com.winworld.coursestools.enums.TradingViewRetryJobStatus;
import com.winworld.coursestools.repository.TradingViewRetryJobRepository;
import com.winworld.coursestools.repository.ops.OpsAlertRepository;
import com.winworld.coursestools.service.ops.OpsAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Daily summary of the previous Moscow day, sent at 09:00 Moscow time. */
@Component
@RequiredArgsConstructor
@Slf4j
public class OpsDigestService {
    static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    private static final int MAX_LISTED = 15;

    private final OpsAlertService opsAlertService;
    private final NamedParameterJdbcTemplate jdbc;
    private final OpsAlertRepository opsAlertRepository;
    private final TradingViewRetryJobRepository retryJobRepository;
    private final AlertDeliveryOpsChecks alertDeliveryOpsChecks;

    public void sendDailyDigest() {
        LocalDate day = LocalDate.now(MOSCOW).minusDays(1);
        OffsetDateTime from = day.atStartOfDay(MOSCOW).toOffsetDateTime();
        OffsetDateTime to = from.plusDays(1);
        StringBuilder body = new StringBuilder();
        section(body, "Customers", () -> customers(from, to));
        section(body, "Renewals awaiting payment", () -> renewalsAwaitingPayment(from, to));
        section(body, "Cancellations", () -> cancellations(from, to));
        section(body, "TradingView access", this::tradingViewAccess);
        section(body, "Alert delivery (last 24h)", this::alertDelivery);
        section(body, "System alerts", () -> systemAlerts(from, to));
        opsAlertService.raiseOnce(OpsAlertCategory.DIGEST, OpsAlertSeverity.INFO,
                "Daily summary for " + day, body.toString().trim(), "digest:" + day);
    }

    private String customers(OffsetDateTime from, OffsetDateTime to) {
        MapSqlParameterSource range = new MapSqlParameterSource()
                .addValue("from", from)
                .addValue("to", to)
                .addValue("fromUtc", from.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime())
                .addValue("toUtc", to.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime());
        Long signups = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE created_at >= :from AND created_at < :to", range, Long.class);
        Long trials = jdbc.queryForObject(
                "SELECT COUNT(*) FROM trial_activations WHERE activation_date >= :fromUtc AND activation_date < :toUtc",
                range, Long.class);
        Map<String, Object> purchases = jdbc.queryForMap("""
                SELECT COUNT(*) AS count, COALESCE(SUM(amount), 0) AS total
                FROM users_transactions
                WHERE transaction_type = 'PURCHASE' AND created_at >= :from AND created_at < :to
                """, range);
        Long conversions = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT t.user_id) FROM users_transactions t
                JOIN trial_activations ta ON ta.user_id = t.user_id
                WHERE t.transaction_type = 'PURCHASE' AND t.created_at >= :from AND t.created_at < :to
                  AND NOT EXISTS (SELECT 1 FROM users_transactions p
                                  WHERE p.user_id = t.user_id AND p.transaction_type = 'PURCHASE'
                                    AND p.created_at < :from)
                """, range, Long.class);
        return "- New sign-ups: **" + signups + "**\n"
                + "- Trials started: **" + trials + "**\n"
                + "- Payments: **" + purchases.get("count") + "** totalling **"
                + OpsChecks.money(((Number) purchases.get("total")).longValue(), "usd") + "**\n"
                + "- Trial users who paid for the first time: **" + conversions + "**";
    }

    private String renewalsAwaitingPayment(OffsetDateTime from, OffsetDateTime to) throws Exception {
        InvoiceListParams params = InvoiceListParams.builder()
                .setStatus(InvoiceListParams.Status.OPEN)
                .setCreated(InvoiceListParams.Created.builder()
                        .setGte(from.toEpochSecond()).setLt(to.toEpochSecond()).build())
                .setLimit(100L)
                .build();
        List<String> lines = new ArrayList<>();
        for (Invoice invoice : Invoice.list(params).getData()) {
            if (!"subscription_cycle".equals(invoice.getBillingReason())) {
                continue;
            }
            lines.add("- " + (invoice.getCustomerEmail() == null ? invoice.getCustomer() : invoice.getCustomerEmail())
                    + ": " + OpsChecks.money(invoice.getAmountDue(), invoice.getCurrency())
                    + ", attempts " + invoice.getAttemptCount()
                    + (invoice.getNextPaymentAttempt() == null ? ", **no automatic retry (customer action needed)**"
                    : ", next try " + OpsChecks.utc(invoice.getNextPaymentAttempt())));
        }
        return lines.isEmpty() ? "None." : "**" + lines.size() + "** renewals not paid yet "
                + "(their access pauses after the settlement window):\n" + limit(lines);
    }

    private String cancellations(OffsetDateTime from, OffsetDateTime to) throws Exception {
        EventListParams params = EventListParams.builder()
                .addType("customer.subscription.updated")
                .addType("customer.subscription.deleted")
                .setCreated(EventListParams.Created.builder()
                        .setGte(from.toEpochSecond()).setLt(to.toEpochSecond()).build())
                .setLimit(100L)
                .build();
        List<String> lines = new ArrayList<>();
        for (Event event : Event.list(params).getData()) {
            StripeObject object;
            try {
                object = event.getDataObjectDeserializer().deserializeUnsafe();
            } catch (Exception e) {
                continue;
            }
            if (!(object instanceof Subscription subscription)) {
                continue;
            }
            Map<String, Object> previous = event.getData().getPreviousAttributes();
            boolean cancelledRenewal = "customer.subscription.updated".equals(event.getType())
                    && Boolean.TRUE.equals(subscription.getCancelAtPeriodEnd())
                    && previous != null && Boolean.FALSE.equals(previous.get("cancel_at_period_end"));
            boolean ended = "customer.subscription.deleted".equals(event.getType());
            if (!cancelledRenewal && !ended) {
                continue;
            }
            String comment = subscription.getCancellationDetails() == null ? null
                    : subscription.getCancellationDetails().getComment();
            String reason = subscription.getCancellationDetails() == null ? null
                    : subscription.getCancellationDetails().getReason();
            lines.add("- " + customerEmail(subscription.getCustomer()) + ": "
                    + (ended ? "subscription ended" : "turned off auto-renew")
                    + (reason == null ? "" : " (" + reason + ")")
                    + (comment == null || comment.isBlank() ? "" : ", said: \"" + comment + "\""));
        }
        return lines.isEmpty() ? "None." : limit(lines);
    }

    private String tradingViewAccess() {
        long dead = retryJobRepository.findAllWithUserByStatus(TradingViewRetryJobStatus.DEAD).size();
        return "- Jobs waiting for an admin (DEAD): **" + dead + "**";
    }

    private String alertDelivery() {
        return alertDeliveryOpsChecks.statsForDigest()
                .map(stats -> {
                    JsonNode day = stats.path("last24h");
                    return "- Alerts received: **" + day.path("alertsReceived").asLong() + "**\n"
                            + "- Telegram messages sent: **" + day.path("messagesSent").asLong() + "**\n"
                            + "- Failed send attempts (retried): **" + day.path("sendAttemptsFailed").asLong() + "**\n"
                            + "- Undelivered after all retries (requeued): **" + day.path("chatsRequeued").asLong() + "**\n"
                            + "- Dropped as too old: **" + day.path("alertsDroppedStale").asLong() + "**\n"
                            + "- Rejected by Telegram (user never started the bot or blocked it): **"
                            + day.path("permanentRejections").asLong() + "**";
                })
                .orElse("Alert bot stats unavailable.");
    }

    private String systemAlerts(OffsetDateTime from, OffsetDateTime to) {
        List<Object[]> rows = opsAlertRepository.countByCategoryBetween(
                from.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime(),
                to.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime());
        if (rows.isEmpty()) {
            return "None.";
        }
        List<String> lines = new ArrayList<>();
        for (Object[] row : rows) {
            lines.add("- " + row[0] + ": " + row[1]);
        }
        return String.join("\n", lines);
    }

    private static String customerEmail(String customerId) {
        try {
            String email = Customer.retrieve(customerId).getEmail();
            return email == null ? customerId : email;
        } catch (Exception e) {
            return customerId;
        }
    }

    private static String limit(List<String> lines) {
        String shown = String.join("\n", lines.subList(0, Math.min(MAX_LISTED, lines.size())));
        return lines.size() > MAX_LISTED ? shown + "\n- … and " + (lines.size() - MAX_LISTED) + " more" : shown;
    }

    @FunctionalInterface
    private interface SectionBody {
        String build() throws Exception;
    }

    private static void section(StringBuilder body, String title, SectionBody content) {
        String text;
        try {
            text = content.build();
        } catch (Exception e) {
            log.warn("Digest section '{}' failed: {}", title, e.getMessage());
            text = "_Could not be computed: " + e.getMessage() + "_";
        }
        body.append("### ").append(title).append('\n').append(text).append("\n\n");
    }
}
