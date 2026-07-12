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

class ChangeTradingViewNameDtoTest {

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
    void rename_appliesSameOneDayBuffer_asGrant() {
        LocalDateTime exp = LocalDateTime.of(2026, 8, 11, 15, 41, 38);
        var dto = ChangeTradingViewNameDto.rename("old", "new", SubscriptionTier.PRO, exp, false);
        assertThat(dto.getExpiration()).isEqualTo(exp.plusDays(1));
    }

    @Test
    void rename_serializesWholeSeconds_andPreservesOldNewKeys() throws Exception {
        LocalDateTime fractional = LocalDateTime.of(2026, 7, 17, 2, 46, 35, 991_576_000);
        var dto = ChangeTradingViewNameDto.rename("old", "new", SubscriptionTier.PRO, fractional, true);
        String json = prodLikeMapper().writeValueAsString(dto);
        assertThat(json).contains("\"old\":\"old\"");
        assertThat(json).contains("\"new\":\"new\"");
        assertThat(json).contains("\"expiration\":\"2026-07-17T02:46:35\"");
        assertThat(json).doesNotContain(".991");
    }
}
