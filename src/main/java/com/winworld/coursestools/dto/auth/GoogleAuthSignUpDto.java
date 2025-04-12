package com.winworld.coursestools.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import static com.winworld.coursestools.constants.RegularExpression.TRADING_VIEW_USERNAME;
import static com.winworld.coursestools.constants.ValidationMessages.NOT_BLANK_MESSAGE;
import static com.winworld.coursestools.constants.ValidationMessages.PATTERN_MESSAGE;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GoogleAuthSignUpDto {
    private static final String USERNAME_SIZE_MESSAGE = "Size must be between 3 and 25 characters";

    @NotBlank(message = NOT_BLANK_MESSAGE)
    @Size(min = 3, max = 25, message = USERNAME_SIZE_MESSAGE)
    @Pattern(regexp = TRADING_VIEW_USERNAME, message = PATTERN_MESSAGE)
    private String tradingViewName;

    private String referrerCode;

    @NotBlank(message = NOT_BLANK_MESSAGE)
    private String authorizationCode;
}
