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
public class ActivateTradingViewAccessDto {

    /**
     * Days of head-room added only to successful customer-payment activations.
     * Manual grants, trials, lifecycle syncs, renames, and Direct Extend stay exact.
     */
    public static final long CUSTOMER_PAYMENT_EXPIRY_BUFFER_DAYS = 1;

    private String email;
    private SubscriptionTier tier;
    @JsonProperty(value = "tv")
    private String tradingViewName;
    // Serialized/deserialized by the app-wide ObjectMapper's ISO_LOCAL_DATE_TIME
    // handling (see ApplicationConfiguration). Intentionally NOT pinned with a strict
    // @JsonFormat pattern: the bot already accepts the fractional/whole-second/no-second
    // forms this produces (verified in prod), and a strict pattern would also constrain
    // DESERIALIZATION and reject legacy fractional-second rows already stored in the
    // trading_view_retry_jobs.payload queue.
    private LocalDateTime expiration;
    private boolean isLifetime;

    private ActivateTradingViewAccessDto(
            String email,
            SubscriptionTier tier,
            String tradingViewName,
            LocalDateTime expiredAt,
            boolean isLifetime
    ) {
        this.email = email;
        this.tier = tier;
        this.tradingViewName = tradingViewName;
        this.expiration = expiredAt;
        this.isLifetime = isLifetime;
    }

    /** Build an exact activation for a non-payment flow. */
    public static ActivateTradingViewAccessDto exactGrant(
            String email,
            SubscriptionTier tier,
            String tradingViewName,
            LocalDateTime expiredAt,
            boolean isLifetime
    ) {
        return new ActivateTradingViewAccessDto(email, tier, tradingViewName, expiredAt, isLifetime);
    }

    /** Build a customer-payment activation with the safety buffer applied once. */
    public static ActivateTradingViewAccessDto customerPaymentGrant(
            String email,
            SubscriptionTier tier,
            String tradingViewName,
            LocalDateTime expiredAt,
            boolean isLifetime
    ) {
        return new ActivateTradingViewAccessDto(
                email,
                tier,
                tradingViewName,
                bufferCustomerPaymentExpiration(expiredAt, isLifetime),
                isLifetime
        );
    }

    private static LocalDateTime bufferCustomerPaymentExpiration(LocalDateTime expiredAt, boolean isLifetime) {
        if (expiredAt == null || isLifetime) {
            return expiredAt;
        }
        return expiredAt.plusDays(CUSTOMER_PAYMENT_EXPIRY_BUFFER_DAYS);
    }
}
