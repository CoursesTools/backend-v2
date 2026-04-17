package com.winworld.coursestools.dto.admin;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

import static com.winworld.coursestools.constants.ValidationMessages.NOT_NULL_MESSAGE;

/**
 * Admin "custom" access update — pure expiredAt bump for a user who already has
 * an active (non-terminated) subscription. Tier and plan are inherited from the
 * existing subscription row.
 */
@Data
public class CustomAccessUpdateDto {
    @NotNull(message = NOT_NULL_MESSAGE)
    private String tradingViewName;

    @NotNull(message = NOT_NULL_MESSAGE)
    @Future
    private LocalDate expiredAt;
}
