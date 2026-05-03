package com.winworld.coursestools.service.payment.impl;

import com.winworld.coursestools.dto.payment.StripeSubscriptionLifecycleDto;
import com.winworld.coursestools.config.props.StripeProperties;
import com.winworld.coursestools.dto.payment.StripeRetrieveDto;
import com.winworld.coursestools.exception.exceptions.SecurityException;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
