package com.winworld.coursestools.service.ops.checks;

import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.model.Dispute;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.StripeObject;
import com.stripe.model.WebhookEndpoint;
import com.stripe.param.EventListParams;
import com.stripe.param.InvoiceListParams;
import com.stripe.param.WebhookEndpointListParams;
import com.winworld.coursestools.config.props.OpsProperties;
import com.winworld.coursestools.enums.OpsAlertCategory;
import com.winworld.coursestools.enums.OpsAlertSeverity;
import com.winworld.coursestools.service.ops.OpsAlertService;
import com.winworld.coursestools.service.payment.impl.StripePaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Stripe health: can Stripe reach us, did every paid invoice give access, disputes and refunds. */
@Component
@Slf4j
public class PaymentOpsChecks {
    private static final List<String> OUR_WEBHOOK_EVENT_TYPES = List.of(
            "invoice.payment_succeeded", "checkout.session.completed",
            "customer.subscription.updated", "customer.subscription.deleted");
    private static final Duration UNDELIVERED_AFTER = Duration.ofMinutes(30);
    private static final Duration PAID_PROCESSING_GRACE = Duration.ofMinutes(15);
    private static final int MAX_LISTED = 10;
    private static final int MAX_SCANNED = 500;

    private final OpsAlertService opsAlertService;
    private final NamedParameterJdbcTemplate jdbc;
    private final OpsProperties opsProperties;
    private final RestTemplate probeClient;

    public PaymentOpsChecks(OpsAlertService opsAlertService, NamedParameterJdbcTemplate jdbc,
                            OpsProperties opsProperties) {
        this.opsAlertService = opsAlertService;
        this.jdbc = jdbc;
        this.opsProperties = opsProperties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        requestFactory.setReadTimeout((int) Duration.ofSeconds(15).toMillis());
        this.probeClient = new RestTemplate(requestFactory);
    }

    public void run() {
        OpsChecks.safely("stripe webhook endpoint", this::checkWebhookEndpoint);
        OpsChecks.safely("stripe undelivered events", this::checkUndeliveredEvents);
        OpsChecks.safely("stripe paid but not processed", this::checkPaidButNotProcessed);
        OpsChecks.safely("stripe disputes and refunds", this::checkDisputesAndRefunds);
    }

    /** The endpoint must exist, be enabled in Stripe, and actually reach our backend. */
    void checkWebhookEndpoint() throws StripeException {
        String problem = null;
        Optional<WebhookEndpoint> endpoint = WebhookEndpoint.list(
                        WebhookEndpointListParams.builder().setLimit(20L).build())
                .getData().stream()
                .filter(candidate -> candidate.getUrl().endsWith(opsProperties.stripeWebhookPath()))
                .findFirst();
        if (endpoint.isEmpty()) {
            problem = "No Stripe webhook endpoint points at `" + opsProperties.stripeWebhookPath() + "`.";
        } else if (!"enabled".equals(endpoint.get().getStatus())) {
            problem = "Stripe webhook endpoint `" + endpoint.get().getUrl() + "` is **"
                    + endpoint.get().getStatus() + "** in Stripe.";
        } else {
            problem = probe(endpoint.get().getUrl());
        }
        opsAlertService.track("payments.stripe-webhook-endpoint", OpsAlertCategory.PAYMENTS,
                OpsAlertSeverity.CRITICAL, problem != null, "Stripe cannot deliver payment webhooks",
                problem == null ? "" : problem
                        + "\n\nPayments are still taken but customers get no access until webhooks flow again.");
    }

    /**
     * Sends an unsigned request to our own webhook URL through the public internet path (DNS, TLS,
     * proxy). Our backend must answer 400 "No signatures found"; anything else means Stripe can't
     * reach us either.
     */
    private String probe(String url) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Stripe-Signature", "t=0,v1=ops-probe");
        try {
            probeClient.postForEntity(url, new HttpEntity<>("{}", headers), String.class);
            return "Probe of `" + url + "` unexpectedly succeeded without a signature.";
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 400 && e.getResponseBodyAsString().contains("signature")) {
                return null;
            }
            return "Probe of `" + url + "` returned HTTP " + e.getStatusCode().value() + " instead of our backend's 400.";
        } catch (RestClientException e) {
            return "Probe of `" + url + "` failed: `" + e.getMessage() + "` (DNS, TLS or connectivity).";
        }
    }

    void checkUndeliveredEvents() throws StripeException {
        long now = Instant.now().getEpochSecond();
        EventListParams params = EventListParams.builder()
                .addAllType(OUR_WEBHOOK_EVENT_TYPES)
                .setCreated(EventListParams.Created.builder().setGte(now - Duration.ofDays(3).toSeconds()).build())
                .setLimit(100L)
                .build();
        List<String> undelivered = new ArrayList<>();
        int scanned = 0;
        for (Event event : Event.list(params).autoPagingIterable()) {
            if (++scanned > MAX_SCANNED) {
                break;
            }
            if (event.getPendingWebhooks() != null && event.getPendingWebhooks() > 0
                    && event.getCreated() < now - UNDELIVERED_AFTER.toSeconds()) {
                undelivered.add("- `" + event.getType() + "` `" + event.getId() + "` created "
                        + OpsChecks.utc(event.getCreated()));
            }
        }
        String body = "**" + undelivered.size() + "** Stripe events still not acknowledged after "
                + UNDELIVERED_AFTER.toMinutes() + " minutes. Stripe retries for 3 days, then gives up; "
                + "resend them from the Stripe dashboard.\n\n"
                + String.join("\n", undelivered.subList(0, Math.min(MAX_LISTED, undelivered.size())))
                + "\n\n_The pending count includes every endpoint (Tilda too); check the delivery log._";
        opsAlertService.track("payments.stripe-undelivered-events", OpsAlertCategory.PAYMENTS,
                OpsAlertSeverity.CRITICAL, !undelivered.isEmpty(), "Stripe events not delivered", body);
    }

    /** Every paid subscription invoice must have extended the customer's access to its paid period. */
    void checkPaidButNotProcessed() throws StripeException {
        long now = Instant.now().getEpochSecond();
        InvoiceListParams params = InvoiceListParams.builder()
                .setStatus(InvoiceListParams.Status.PAID)
                .setCreated(InvoiceListParams.Created.builder().setGte(now - Duration.ofHours(48).toSeconds()).build())
                .setLimit(100L)
                .build();
        List<String> missing = new ArrayList<>();
        int scanned = 0;
        for (Invoice invoice : Invoice.list(params).autoPagingIterable()) {
            if (++scanned > MAX_SCANNED) {
                break;
            }
            Long paidAt = invoice.getStatusTransitions() == null ? null : invoice.getStatusTransitions().getPaidAt();
            Long paidPeriodEnd = StripePaymentService.paidPeriodEnd(invoice);
            if (invoice.getSubscription() == null || invoice.getAmountPaid() == null || invoice.getAmountPaid() == 0
                    || paidAt == null || paidAt > now - PAID_PROCESSING_GRACE.toSeconds() || paidPeriodEnd == null) {
                continue;
            }
            OffsetDateTime localExpiry = jdbc.queryForObject("""
                            SELECT MAX(expired_at) FROM users_subscriptions
                            WHERE payment_provider_data ->> 'subscriptionId' = :subscriptionId
                            """,
                    new MapSqlParameterSource("subscriptionId", invoice.getSubscription()), OffsetDateTime.class);
            if (localExpiry == null || localExpiry.toEpochSecond() < paidPeriodEnd - 60) {
                missing.add("- `" + invoice.getId() + "` " + OpsChecks.money(invoice.getAmountPaid(), invoice.getCurrency())
                        + " " + (invoice.getCustomerEmail() == null ? "" : invoice.getCustomerEmail())
                        + ", paid " + OpsChecks.utc(paidAt) + ", access should run to " + OpsChecks.utc(paidPeriodEnd));
            }
        }
        String body = "**" + missing.size() + "** paid Stripe invoices did not give the customer access for the "
                + "period they paid for:\n\n"
                + String.join("\n", missing.subList(0, Math.min(MAX_LISTED, missing.size())))
                + "\n\nResend the invoice's `invoice.payment_succeeded` event from the Stripe dashboard.";
        opsAlertService.track("payments.paid-not-processed", OpsAlertCategory.PAYMENTS,
                OpsAlertSeverity.CRITICAL, !missing.isEmpty(), "Payments taken but access not granted", body);
    }

    void checkDisputesAndRefunds() throws StripeException {
        long now = Instant.now().getEpochSecond();
        EventListParams params = EventListParams.builder()
                .addType("charge.dispute.created")
                .addType("charge.refunded")
                .setCreated(EventListParams.Created.builder().setGte(now - Duration.ofHours(24).toSeconds()).build())
                .setLimit(100L)
                .build();
        for (Event event : Event.list(params).getData()) {
            StripeObject object = deserialize(event);
            if ("charge.dispute.created".equals(event.getType())) {
                String body = object instanceof Dispute dispute
                        ? "Amount " + OpsChecks.money(dispute.getAmount(), dispute.getCurrency())
                        + ", reason `" + dispute.getReason() + "`, charge `" + dispute.getCharge() + "`."
                        + (dispute.getEvidenceDetails() != null && dispute.getEvidenceDetails().getDueBy() != null
                        ? "\nEvidence due by " + OpsChecks.utc(dispute.getEvidenceDetails().getDueBy()) + "." : "")
                        : "Event `" + event.getId() + "`.";
                opsAlertService.raiseOnce(OpsAlertCategory.PAYMENTS, OpsAlertSeverity.CRITICAL,
                        "New Stripe dispute", body + "\n\nRespond in the Stripe dashboard before the deadline.",
                        "stripe-event:" + event.getId());
            } else {
                String body = object instanceof Charge charge
                        ? "Refunded " + OpsChecks.money(charge.getAmountRefunded(), charge.getCurrency())
                        + " on charge `" + charge.getId() + "`"
                        + (charge.getBillingDetails() != null && charge.getBillingDetails().getEmail() != null
                        ? " (" + charge.getBillingDetails().getEmail() + ")" : "") + "."
                        : "Event `" + event.getId() + "`.";
                opsAlertService.raiseOnce(OpsAlertCategory.PAYMENTS, OpsAlertSeverity.WARNING,
                        "Stripe refund issued", body, "stripe-event:" + event.getId());
            }
        }
    }

    private static StripeObject deserialize(Event event) {
        try {
            return event.getDataObjectDeserializer().deserializeUnsafe();
        } catch (Exception e) {
            log.warn("Could not read Stripe event {} data: {}", event.getId(), e.getMessage());
            return null;
        }
    }
}
