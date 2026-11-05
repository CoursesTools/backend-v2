package com.winworld.coursestools.service.payment.impl;

import com.stripe.model.Invoice;
import com.stripe.model.InvoiceLineItem;
import com.stripe.model.InvoiceLineItemCollection;
import com.stripe.model.Subscription;
import com.winworld.coursestools.dto.payment.ProcessPaymentDto;
import com.winworld.coursestools.dto.payment.StripeSubscriptionLifecycleDto;
import com.winworld.coursestools.config.props.StripeProperties;
import com.winworld.coursestools.dto.payment.StripeRetrieveDto;
import com.winworld.coursestools.exception.exceptions.SecurityException;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

class StripePaymentServiceTest {
    private static final String WEBHOOK_SECRET = "whsec_test_secret";

    private final StripePaymentService stripePaymentService = new StripePaymentService(
            new StripeProperties("sk_test_secret", WEBHOOK_SECRET, "coupon_test")
    );

    @Test
    void processWebhook_UnsupportedEvent_ReturnsNullAck() {
        String payload = """
                {
                  "id": "evt_unsupported",
                  "object": "event",
                  "created": 1712562141,
                  "livemode": false,
                  "type": "customer.created",
                  "data": {
                    "object": {
                      "id": "cus_test",
                      "object": "customer"
                    }
                  }
                }
                """;
        StripeRetrieveDto dto = StripeRetrieveDto.builder()
                .payload(payload)
                .signature(createStripeSignatureHeader(payload, WEBHOOK_SECRET))
                .build();

        assertNull(stripePaymentService.processWebhook(dto));
    }

    @Test
    void processWebhook_InvalidSignature_ThrowsSecurityException() {
        String payload = """
                {
                  "id": "evt_unsupported",
                  "object": "event",
                  "created": 1712562141,
                  "livemode": false,
                  "type": "customer.created",
                  "data": {
                    "object": {
                      "id": "cus_test",
                      "object": "customer"
                    }
                  }
                }
                """;
        StripeRetrieveDto dto = StripeRetrieveDto.builder()
                .payload(payload)
                .signature(createStripeSignatureHeader(payload, "wrong_secret"))
                .build();

        assertThrows(SecurityException.class, () -> stripePaymentService.processWebhook(dto));
    }

    @Test
    void processSubscriptionLifecycleWebhook_UpdatedEvent_ReturnsSubscriptionState() {
        String payload = """
                {
                  "id": "evt_subscription_updated",
                  "object": "event",
                  "created": 1777680000,
                  "livemode": true,
                  "type": "customer.subscription.updated",
                  "data": {
                    "object": {
                      "id": "sub_sync",
                      "object": "subscription",
                      "current_period_end": 1777698505,
                      "status": "active",
                      "cancel_at_period_end": true
                    }
                  }
                }
                """;
        StripeRetrieveDto dto = signedDto(payload);

        StripeSubscriptionLifecycleDto lifecycleDto =
                stripePaymentService.processSubscriptionLifecycleWebhook(dto);

        assertTrue(stripePaymentService.isSubscriptionLifecycleEvent(dto));
        assertFalse(stripePaymentService.isSubscriptionDeletedEvent(dto));
        assertEquals("sub_sync", lifecycleDto.getSubscriptionId());
        assertEquals(1777698505L, lifecycleDto.getCurrentPeriodEnd());
        assertEquals("active", lifecycleDto.getStatus());
        assertTrue(lifecycleDto.getCancelAtPeriodEnd());
    }

    @Test
    void processSubscriptionLifecycleWebhook_DeletedEvent_IsDetectedAsDeleted() {
        String payload = """
                {
                  "id": "evt_subscription_deleted",
                  "object": "event",
                  "created": 1777680000,
                  "livemode": true,
                  "type": "customer.subscription.deleted",
                  "data": {
                    "object": {
                      "id": "sub_deleted",
                      "object": "subscription",
                      "current_period_end": 1777698505,
                      "status": "canceled",
                      "cancel_at_period_end": false
                    }
                  }
                }
                """;
        StripeRetrieveDto dto = signedDto(payload);

        StripeSubscriptionLifecycleDto lifecycleDto =
                stripePaymentService.processSubscriptionLifecycleWebhook(dto);

        assertTrue(stripePaymentService.isSubscriptionLifecycleEvent(dto));
        assertTrue(stripePaymentService.isSubscriptionDeletedEvent(dto));
        assertEquals("sub_deleted", lifecycleDto.getSubscriptionId());
        assertEquals("canceled", lifecycleDto.getStatus());
        assertFalse(lifecycleDto.getCancelAtPeriodEnd());
    }

    @Test
    void processPayment_LateInvoiceGrantsOnlyItsOwnPaidPeriod_NotTheSubscriptionsNewerUnpaidPeriod() {
        long paidPeriodEnd = 1792662828L;
        long unpaidRolledPeriodEnd = 1795254828L;
        String payload = """
                {
                  "id": "evt_invoice_paid",
                  "object": "event",
                  "created": 1792662900,
                  "livemode": true,
                  "type": "invoice.payment_succeeded",
                  "data": {
                    "object": {
                      "id": "in_paid",
                      "object": "invoice",
                      "customer": "cus_paid",
                      "subscription": "sub_paid",
                      "lines": {
                        "object": "list",
                        "data": [
                          {
                            "id": "il_paid",
                            "object": "line_item",
                            "metadata": { "order_id": "857" },
                            "period": { "start": 1790070828, "end": %d }
                          }
                        ]
                      }
                    }
                  }
                }
                """.formatted(paidPeriodEnd);
        Subscription liveSubscription = new Subscription();
        liveSubscription.setId("sub_paid");
        liveSubscription.setCurrentPeriodEnd(unpaidRolledPeriodEnd);

        try (MockedStatic<Subscription> stripeSubscription = mockStatic(Subscription.class)) {
            stripeSubscription.when(() -> Subscription.retrieve("sub_paid")).thenReturn(liveSubscription);

            ProcessPaymentDto result = stripePaymentService.processPayment(signedDto(payload));

            assertEquals(857, result.getOrderId());
            assertEquals("in_paid", result.getPaymentProviderData().get(StripePaymentService.INVOICE_ID));
            assertEquals(paidPeriodEnd, result.getPaymentProviderData().get(StripePaymentService.CURRENT_PERIOD_END));
        }
    }

    @Test
    void paidPeriodEnd_ProrationInvoiceUsesLatestLinePeriodEnd() {
        Invoice invoice = invoiceWithLinePeriodEnds(1792000000L, 1821536000L);

        assertEquals(1821536000L, StripePaymentService.paidPeriodEnd(invoice));
    }

    @Test
    void paidPeriodEnd_InvoiceWithoutLinePeriodsReturnsNull() {
        assertNull(StripePaymentService.paidPeriodEnd(invoiceWithLinePeriodEnds()));
    }

    private static Invoice invoiceWithLinePeriodEnds(Long... periodEnds) {
        List<InvoiceLineItem> items = new ArrayList<>();
        for (Long periodEnd : periodEnds) {
            InvoiceLineItem.Period period = new InvoiceLineItem.Period();
            period.setEnd(periodEnd);
            InvoiceLineItem item = new InvoiceLineItem();
            item.setPeriod(period);
            items.add(item);
        }
        InvoiceLineItemCollection lines = new InvoiceLineItemCollection();
        lines.setData(items);
        Invoice invoice = new Invoice();
        invoice.setLines(lines);
        return invoice;
    }

    private static StripeRetrieveDto signedDto(String payload) {
        return StripeRetrieveDto.builder()
                .payload(payload)
                .signature(createStripeSignatureHeader(payload, WEBHOOK_SECRET))
                .build();
    }

    private static String createStripeSignatureHeader(String payload, String secret) {
        long timestamp = Instant.now().getEpochSecond();
        return "t=%d,v1=%s".formatted(timestamp, hmacSha256(timestamp + "." + payload, secret));
    }

    private static String hmacSha256(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to create Stripe test signature", e);
        }
    }
}
