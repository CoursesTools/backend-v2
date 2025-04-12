package com.winworld.coursestools.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.winworld.coursestools.validation.groups.AuthValidationGroup;
import com.winworld.coursestools.validation.groups.OAuthValidationGroup;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuthSignInDto {
    private static final String NOT_BLANK_MESSAGE = "The field must not be empty";
    private static final String EMAIL_MESSAGE = "Field is not email";
    private static final String PASSWORD_SIZE_MESSAGE = "Size must be between 7 and 64 characters";

    @NotBlank(message = NOT_BLANK_MESSAGE, groups = AuthValidationGroup.class)
    @Email(message = EMAIL_MESSAGE, groups = AuthValidationGroup.class)
    private String email;

    @NotBlank(message = NOT_BLANK_MESSAGE, groups = AuthValidationGroup.class)
    @Size(min = 7, max = 64, message = PASSWORD_SIZE_MESSAGE, groups = AuthValidationGroup.class)
    private String password;

    @NotBlank(message = NOT_BLANK_MESSAGE, groups = OAuthValidationGroup.class)
    @JsonProperty("authorization_code")
    protected String authorizationCode;
}
