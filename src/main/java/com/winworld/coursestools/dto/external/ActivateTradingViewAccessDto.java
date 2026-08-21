package com.winworld.coursestools.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.winworld.coursestools.enums.SubscriptionTier;
import com.winworld.coursestools.enums.TradingViewExpirationPolicy;
import com.winworld.coursestools.event.SubscriptionChangeStatusEvent;
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
    // The access server receives whole seconds only. The explicit setter keeps
    // legacy fractional retry payloads readable while normalizing them before replay.
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
        setExpiration(expiredAt);
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

    public static ActivateTradingViewAccessDto fromSubscriptionEvent(SubscriptionChangeStatusEvent event) {
        if (event.getTradingViewExpirationPolicy() == TradingViewExpirationPolicy.CUSTOMER_PAYMENT_BUFFER) {
            return customerPaymentGrant(
                    event.getEmail(), event.getTier(), event.getTradingViewUsername(),
                    event.getExpiration(), event.isLifetime());
        }
        return exactGrant(
                event.getEmail(), event.getTier(), event.getTradingViewUsername(),
                event.getExpiration(), event.isLifetime());
    }

    public void setExpiration(LocalDateTime expiration) {
        this.expiration = TradingViewTimestamp.wholeSeconds(expiration);
    }

    private static LocalDateTime bufferCustomerPaymentExpiration(LocalDateTime expiredAt, boolean isLifetime) {
        if (expiredAt == null || isLifetime) {
            return expiredAt;
        }
        return expiredAt.plusDays(CUSTOMER_PAYMENT_EXPIRY_BUFFER_DAYS);
    }
}
