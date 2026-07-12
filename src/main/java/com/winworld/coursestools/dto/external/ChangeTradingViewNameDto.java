package com.winworld.coursestools.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.winworld.coursestools.enums.SubscriptionTier;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// Persisted as JSON in trading_view_retry_jobs.payload; tolerate unknown fields
// so future schema changes don't poison stored retry payloads.
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChangeTradingViewNameDto {
    @JsonProperty("old")
    private String oldName;
    @JsonProperty("new")
    private String newName;
    private SubscriptionTier tier;
    // See ActivateTradingViewAccessDto.expiration: deliberately no strict @JsonFormat,
    // to keep legacy retry-queue payloads deserializable.
    private LocalDateTime expiration;
    private boolean isLifetime;

    /**
     * Build a rename payload, padding the expiry by
     * {@link ActivateTradingViewAccessDto#BOT_EXPIRY_BUFFER_DAYS} exactly like a grant
     * so a rename does not silently shorten the buffered access window.
     */
    public static ChangeTradingViewNameDto rename(
            String oldName,
            String newName,
            SubscriptionTier tier,
            LocalDateTime expiredAt,
            boolean isLifetime
    ) {
        return new ChangeTradingViewNameDto(
                oldName,
                newName,
                tier,
                ActivateTradingViewAccessDto.bufferBotExpiration(expiredAt, isLifetime),
                isLifetime);
    }
}
