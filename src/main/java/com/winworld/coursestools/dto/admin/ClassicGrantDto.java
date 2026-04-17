package com.winworld.coursestools.dto.admin;

import com.winworld.coursestools.enums.Plan;
import com.winworld.coursestools.enums.SubscriptionTier;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

import static com.winworld.coursestools.constants.ValidationMessages.NOT_NULL_MESSAGE;

/**
 * Admin "classic" access grant — simulates a payment webhook outcome. Used for
 * MONTH/YEAR/LIFETIME grants (expiry derived from plan duration) and TRIAL
 * grants (expiry supplied via trialExpiresAt).
 */
@Data
public class ClassicGrantDto {
    @NotNull(message = NOT_NULL_MESSAGE)
    private String tradingViewName;

    @NotNull(message = NOT_NULL_MESSAGE)
    private SubscriptionTier tier;

    @NotNull(message = NOT_NULL_MESSAGE)
    private Plan plan;

    @Future
    private LocalDate trialExpiresAt;

    /**
     * When true, the user's existing subscription is mutated to the new tier/plan
     * but its expiredAt is preserved. Use this for tier swaps (e.g. ESSENTIALS →
     * PRO) without resetting the user's billing timer. Requires an existing
     * non-terminated subscription. Only valid when plan is MONTH or YEAR.
     */
    private boolean keepExpirationDate;

    @AssertTrue(message = "trialExpiresAt is required (and must be in the future) when plan=TRIAL")
    public boolean isTrialExpiryConsistent() {
        if (plan != Plan.TRIAL) return true;
        return trialExpiresAt != null;
    }

    @AssertTrue(message = "keepExpirationDate is only valid when plan is MONTH or YEAR")
    public boolean isKeepExpirationConsistent() {
        if (!keepExpirationDate) return true;
        return plan == Plan.MONTH || plan == Plan.YEAR;
    }
}
