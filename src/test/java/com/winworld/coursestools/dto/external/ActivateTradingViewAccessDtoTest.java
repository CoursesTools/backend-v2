package com.winworld.coursestools.dto.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.winworld.coursestools.config.ApplicationConfiguration;
import com.winworld.coursestools.enums.SubscriptionTier;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ActivateTradingViewAccessDtoTest {

    // Exercise the ACTUAL production bean rather than a hand-copied mirror.
    private final ObjectMapper mapper = new ApplicationConfiguration().objectMapper();

    @Test
    void customerPaymentGrant_addsOneDayBuffer_forNonLifetime() {
        LocalDateTime exp = LocalDateTime.of(2026, 8, 11, 15, 41, 38);
        var dto = ActivateTradingViewAccessDto.customerPaymentGrant(
                "e@x.com", SubscriptionTier.PRO, "nick", exp, false);
        assertThat(dto.getExpiration()).isEqualTo(exp.plusDays(1));
    }

    @Test
    void customerPaymentGrant_skipsBuffer_forLifetime() {
        LocalDateTime exp = LocalDateTime.of(2100, 12, 31, 23, 59, 59);
        var dto = ActivateTradingViewAccessDto.customerPaymentGrant(
                "e@x.com", SubscriptionTier.PRO, "nick", exp, true);
        assertThat(dto.getExpiration()).isEqualTo(exp);
    }

    @Test
    void exactGrant_doesNotBufferNonPaymentExpiration() {
        LocalDateTime exp = LocalDateTime.of(2026, 8, 11, 15, 41, 38);
        var dto = ActivateTradingViewAccessDto.exactGrant(
                "e@x.com", SubscriptionTier.PRO, "nick", exp, false);
        assertThat(dto.getExpiration()).isEqualTo(exp);
    }

    @Test
    void wireContract_keysUnchanged() throws Exception {
        var dto = ActivateTradingViewAccessDto.exactGrant("e@x.com", SubscriptionTier.PRO, "nick",
                LocalDateTime.of(2026, 8, 11, 15, 41, 38), false);
        String json = mapper.writeValueAsString(dto);
        assertThat(json).contains("\"tv\":\"nick\"");     // @JsonProperty("tv")
        assertThat(json).contains("\"lifetime\":false");  // Lombok isLifetime -> "lifetime" key (bot contract preserved)
        assertThat(json).contains("\"tier\":\"PRO\"");
    }

    @Test
    void retryQueueRoundTrip_preservesValue_includingFractionalSeconds() throws Exception {
        // The retry queue serializes the padded dto to trading_view_retry_jobs.payload and
        // later deserializes it back; that round-trip must be lossless for now()-derived
        // (fractional-second) expiries, which are the common case for trials.
        LocalDateTime fractional = LocalDateTime.of(2026, 7, 17, 2, 46, 35, 991_576_000);
        var dto = ActivateTradingViewAccessDto.customerPaymentGrant(
                "e@x.com", SubscriptionTier.PRO, "nick", fractional, false);
        String json = mapper.writeValueAsString(dto);
        var back = mapper.readValue(json, ActivateTradingViewAccessDto.class);
        assertThat(back.getExpiration()).isEqualTo(dto.getExpiration());
        assertThat(back.getExpiration()).isEqualTo(fractional.plusDays(1));
        assertThat(back.getTradingViewName()).isEqualTo("nick");
    }

    @Test
    void deserializesLegacyFractionalSecondPayload() throws Exception {
        // Guards against re-introducing a strict @JsonFormat: legacy queue rows written by the
        // old serializer carry fractional seconds and must still parse after deploy.
        String legacy = "{\"email\":\"e@x.com\",\"tier\":\"PRO\",\"tv\":\"nick\","
                + "\"expiration\":\"2026-07-17T02:46:35.991576\",\"lifetime\":false}";
        var dto = mapper.readValue(legacy, ActivateTradingViewAccessDto.class);
        assertThat(dto.getExpiration()).isEqualTo(LocalDateTime.of(2026, 7, 17, 2, 46, 35, 991_576_000));
        assertThat(dto.getTradingViewName()).isEqualTo("nick");
    }
}
