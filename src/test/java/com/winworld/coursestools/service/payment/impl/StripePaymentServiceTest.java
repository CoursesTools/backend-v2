package com.winworld.coursestools.service.payment.impl;

import com.winworld.coursestools.config.props.StripeProperties;
import com.winworld.coursestools.dto.payment.StripeRetrieveDto;
import com.winworld.coursestools.exception.exceptions.SecurityException;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
