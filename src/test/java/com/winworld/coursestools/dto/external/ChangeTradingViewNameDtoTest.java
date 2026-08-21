package com.winworld.coursestools.dto.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.winworld.coursestools.config.ApplicationConfiguration;
import com.winworld.coursestools.enums.SubscriptionTier;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeTradingViewNameDtoTest {

    private final ObjectMapper mapper = new ApplicationConfiguration().objectMapper();

    @Test
    void rename_usesExactDatabaseExpiration() {
        LocalDateTime exp = LocalDateTime.of(2026, 8, 11, 15, 41, 38);
        var dto = ChangeTradingViewNameDto.rename("old", "new", SubscriptionTier.PRO, exp, false);
        assertThat(dto.getExpiration()).isEqualTo(exp);
    }

    @Test
    void rename_skipsBuffer_forLifetime() {
        LocalDateTime exp = LocalDateTime.of(2100, 12, 31, 23, 59, 59);
        var dto = ChangeTradingViewNameDto.rename("old", "new", SubscriptionTier.PRO, exp, true);
        assertThat(dto.getExpiration()).isEqualTo(exp);
    }

    @Test
    void wireContract_preservesOldNewKeys_andRoundTrips() throws Exception {
        LocalDateTime fractional = LocalDateTime.of(2026, 7, 17, 2, 46, 35, 991_576_000);
        var dto = ChangeTradingViewNameDto.rename("old", "new", SubscriptionTier.PRO, fractional, true);
        String json = mapper.writeValueAsString(dto);
        assertThat(json).contains("\"old\":\"old\"");
        assertThat(json).contains("\"new\":\"new\"");
        assertThat(json).contains("\"expiration\":\"2026-07-17T02:46:35\"");
        assertThat(json).doesNotContain("991576");
        var back = mapper.readValue(json, ChangeTradingViewNameDto.class);
        assertThat(back.getExpiration()).isEqualTo(dto.getExpiration());
    }

    @Test
    void legacyFractionalRetry_normalizesBeforeRenameDelivery() throws Exception {
        String legacy = "{\"old\":\"old\",\"new\":\"new\",\"tier\":\"PRO\","
                + "\"expiration\":\"2026-07-17T02:46:35.991576\",\"lifetime\":false}";

        var dto = mapper.readValue(legacy, ChangeTradingViewNameDto.class);

        assertThat(dto.getExpiration()).isEqualTo(LocalDateTime.of(2026, 7, 17, 2, 46, 35));
    }
}
