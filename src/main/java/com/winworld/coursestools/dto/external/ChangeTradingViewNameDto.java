package com.winworld.coursestools.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.winworld.coursestools.enums.SubscriptionTier;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// Persisted as JSON in trading_view_retry_jobs.payload; tolerate unknown fields
// so future schema changes don't poison stored retry payloads.
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
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

    private ChangeTradingViewNameDto(
            String oldName,
            String newName,
            SubscriptionTier tier,
            LocalDateTime expiredAt,
            boolean isLifetime
    ) {
        this.oldName = oldName;
        this.newName = newName;
        this.tier = tier;
        this.expiration = expiredAt;
        this.isLifetime = isLifetime;
    }

    /** Renames are non-payment synchronization and use the exact DB expiration. */
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
                expiredAt,
                isLifetime);
    }
}
