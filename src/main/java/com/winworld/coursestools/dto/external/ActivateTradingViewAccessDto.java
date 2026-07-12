package com.winworld.coursestools.dto.external;

import com.fasterxml.jackson.annotation.JsonFormat;
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
public class ActivateTradingViewAccessDto {

    /**
     * Days of head-room added to the real subscription expiry before it is sent to
     * the TradingView bot. The bot receives a naive (offset-less) timestamp; on
     * Moscow-time infrastructure it can read that value a few hours early, and
     * Stripe's renewal webhook can land slightly after the period boundary. A
     * one-day pad keeps bot access alive across both gaps so paying users are never
     * dropped shortly before their auto-charge. The padded value is what gets
     * persisted to the retry queue, so retries replay the same expiration without
     * compounding the pad.
     */
    public static final long BOT_EXPIRY_BUFFER_DAYS = 1;

    private String email;
    private SubscriptionTier tier;
    @JsonProperty(value = "tv")
    private String tradingViewName;
    // Fixed whole-second format, scoped to this bot payload ONLY (does not touch the
    // global ObjectMapper / API responses). Guarantees a stable "yyyy-MM-ddTHH:mm:ss"
    // shape regardless of sub-second precision or :00 seconds, overriding the module's
    // default ISO_LOCAL_DATE_TIME serializer for this field.
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime expiration;
    private boolean isLifetime;

    /**
     * Build a grant payload, padding the expiry by {@link #BOT_EXPIRY_BUFFER_DAYS}
     * (skipped for lifetime, which is already effectively unbounded). Prefer this
     * factory over the all-args constructor at every call site so the pad is applied
     * exactly once.
     */
    public static ActivateTradingViewAccessDto grant(
            String email,
            SubscriptionTier tier,
            String tradingViewName,
            LocalDateTime expiredAt,
            boolean isLifetime
    ) {
        return new ActivateTradingViewAccessDto(
                email, tier, tradingViewName, bufferBotExpiration(expiredAt, isLifetime), isLifetime);
    }

    /** Pad a subscription expiry for the TV bot. See {@link #BOT_EXPIRY_BUFFER_DAYS}. */
    public static LocalDateTime bufferBotExpiration(LocalDateTime expiredAt, boolean isLifetime) {
        if (expiredAt == null || isLifetime) {
            return expiredAt;
        }
        return expiredAt.plusDays(BOT_EXPIRY_BUFFER_DAYS);
    }
}
