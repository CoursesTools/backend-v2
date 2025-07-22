package com.winworld.coursestools.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.winworld.coursestools.validation.groups.AuthValidationGroup;
import com.winworld.coursestools.validation.groups.OAuthValidationGroup;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import static com.winworld.coursestools.constants.RegularExpression.TRADING_VIEW_USERNAME;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuthSignUpDto {
    private static final String NOT_BLANK_MESSAGE = "The field must not be empty";
    private static final String USERNAME_SIZE_MESSAGE = "Size must be between 3 and 25 characters";
    private static final String PASSWORD_SIZE_MESSAGE = "Size must be between 7 and 64 characters";
    private static final String PATTERN_MESSAGE = "Field not match the pattern";
    private static final String EMAIL_MESSAGE = "Field is not email";
    private static final String POSITIVE_MESSAGE = "Field must be greater than 0";

    @NotBlank(message = NOT_BLANK_MESSAGE, groups = AuthValidationGroup.class)
    @Email(message = EMAIL_MESSAGE, groups = AuthValidationGroup.class)
    private String email;

    @NotBlank(message = NOT_BLANK_MESSAGE)
    @Size(min = 3, max = 25, message = USERNAME_SIZE_MESSAGE)
    @Pattern(regexp = TRADING_VIEW_USERNAME, message = PATTERN_MESSAGE)
    @JsonProperty("trading_view_name")
    private String tradingViewName;

    @Positive(message = POSITIVE_MESSAGE)
    @JsonProperty("referrer_code")
    private String referrerCode;

    @NotBlank(message = NOT_BLANK_MESSAGE, groups = AuthValidationGroup.class)
    @Size(min = 7, max = 64, message = PASSWORD_SIZE_MESSAGE, groups = AuthValidationGroup.class)
    private String password;

    @NotBlank(message = NOT_BLANK_MESSAGE, groups = AuthValidationGroup.class)
    @Size(min = 7, max = 64, message = PASSWORD_SIZE_MESSAGE, groups = AuthValidationGroup.class)
    @JsonProperty("confirm_password")
    private String confirmPassword;

    @NotBlank(message = NOT_BLANK_MESSAGE, groups = OAuthValidationGroup.class)
    @JsonProperty("authorization_code")
    protected String authorizationCode;
}
