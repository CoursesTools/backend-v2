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

    @AssertTrue(message = "trialExpiresAt is required (and must be in the future) when plan=TRIAL")
    public boolean isTrialExpiryConsistent() {
        if (plan != Plan.TRIAL) return true;
        return trialExpiresAt != null;
    }
}
