package com.winworld.coursestools.dto.user;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import static com.winworld.coursestools.constants.RegularExpression.TRADING_VIEW_USERNAME;
import static com.winworld.coursestools.constants.ValidationMessages.PATTERN_MESSAGE;

@Data
public class UpdateUserTradingViewDto {
    @Pattern(regexp = TRADING_VIEW_USERNAME, message = PATTERN_MESSAGE)
    @NotNull
    private String tradingViewName;
}
