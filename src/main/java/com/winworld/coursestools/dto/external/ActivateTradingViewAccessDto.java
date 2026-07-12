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
public class ActivateTradingViewAccessDto {

    /**
     * Days of head-room added to the real subscription expiry before it is sent to
     * the TradingView bot, applied to EVERY non-lifetime grant. The bot receives a
     * naive (offset-less) timestamp; on Moscow-time infrastructure it can read that
     * value a few hours early, and Stripe's renewal webhook can land slightly after
     * the period boundary — a one-day pad keeps paying users from being dropped just
     * before their auto-charge. Because the pad is uniform (there is no bot-revoke
     * channel), it also grants trials and canceled/terminated subs up to one extra
     * day of bot access; that is an accepted trade-off. The padded value is persisted
     * to the retry queue, so retries replay the same expiration without compounding.
     */
    public static final long BOT_EXPIRY_BUFFER_DAYS = 1;

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
