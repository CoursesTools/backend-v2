package com.winworld.coursestools.dto.external;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.winworld.coursestools.enums.SubscriptionTier;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;

class ActivateTradingViewAccessDtoTest {

    // Mirrors ApplicationConfiguration.objectMapper(): a globally-registered
    // ISO_LOCAL_DATE_TIME serializer for LocalDateTime. The DTO's field-level
    // @JsonFormat must override it for the "expiration" property.
    private ObjectMapper prodLikeMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        javaTimeModule.addSerializer(LocalDateTime.class,
                new LocalDateTimeSerializer(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        mapper.registerModule(javaTimeModule);
        return mapper;
    }

    @Test
    void grant_addsOneDayBuffer_forNonLifetime() {
        LocalDateTime exp = LocalDateTime.of(2026, 8, 11, 15, 41, 38);
        var dto = ActivateTradingViewAccessDto.grant("e@x.com", SubscriptionTier.PRO, "nick", exp, false);
        assertThat(dto.getExpiration()).isEqualTo(exp.plusDays(1));
    }

    @Test
    void grant_skipsBuffer_forLifetime() {
        LocalDateTime exp = LocalDateTime.of(2100, 12, 31, 23, 59, 59);
        var dto = ActivateTradingViewAccessDto.grant("e@x.com", SubscriptionTier.PRO, "nick", exp, true);
        assertThat(dto.getExpiration()).isEqualTo(exp);
    }

    @Test
    void bufferBotExpiration_isNullSafe() {
        assertThat(ActivateTradingViewAccessDto.bufferBotExpiration(null, false)).isNull();
    }

    @Test
    void expiration_serializesToWholeSeconds_droppingSubSecondPrecision() throws Exception {
        // Reproduces a real prod trial value: 2026-07-17T02:46:35.991576
        LocalDateTime fractional = LocalDateTime.of(2026, 7, 17, 2, 46, 35, 991_576_000);
        var dto = ActivateTradingViewAccessDto.grant("e@x.com", SubscriptionTier.PRO, "nick", fractional, true);
        String json = prodLikeMapper().writeValueAsString(dto);
        assertThat(json).contains("\"expiration\":\"2026-07-17T02:46:35\"");
        assertThat(json).doesNotContain(".991");
    }

    @Test
    void expiration_alwaysIncludesSeconds_evenWhenZero() throws Exception {
        // Bare ISO_LOCAL_DATE_TIME would emit "...T02:46" (no seconds) here; @JsonFormat forces ss.
        LocalDateTime wholeMinute = LocalDateTime.of(2026, 7, 17, 2, 46, 0);
        var dto = ActivateTradingViewAccessDto.grant("e@x.com", SubscriptionTier.PRO, "nick", wholeMinute, true);
        String json = prodLikeMapper().writeValueAsString(dto);
        assertThat(json).contains("\"expiration\":\"2026-07-17T02:46:00\"");
    }

    @Test
    void wireContract_keysUnchanged() throws Exception {
        var dto = ActivateTradingViewAccessDto.grant("e@x.com", SubscriptionTier.PRO, "nick",
                LocalDateTime.of(2026, 8, 11, 15, 41, 38), false);
        String json = prodLikeMapper().writeValueAsString(dto);
        assertThat(json).contains("\"tv\":\"nick\"");     // @JsonProperty("tv")
        assertThat(json).contains("\"lifetime\":false");  // Lombok isLifetime -> "lifetime" key (bot contract preserved)
        assertThat(json).contains("\"tier\":\"PRO\"");
    }
}
