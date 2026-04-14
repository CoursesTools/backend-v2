package com.winworld.coursestools.dto.admin;

import com.winworld.coursestools.enums.SubscriptionTier;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

import static com.winworld.coursestools.constants.ValidationMessages.NOT_NULL_MESSAGE;

@Data
public class ChangeUserAccessDto {
    private Boolean isLifetime;
    private Boolean isTrial;
    @NotNull(message = NOT_NULL_MESSAGE)
    private String tradingViewName;
    @Nullable
    private LocalDate expiredAt;
    @NotNull(message = NOT_NULL_MESSAGE)
    private SubscriptionTier tier;
}
