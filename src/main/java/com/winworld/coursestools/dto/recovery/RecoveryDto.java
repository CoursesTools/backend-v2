package com.winworld.coursestools.dto.recovery;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RecoveryDto {
    private static final String NOT_BLANK_MESSAGE = "The field must not be empty";
    private static final String TOKEN_SIZE_MESSAGE = "Size must be 8 characters";
    private static final String PASSWORD_SIZE_MESSAGE = "Size must be between 7 and 64 characters";

    @NotBlank(message = NOT_BLANK_MESSAGE)
    @Size(min = 8, max = 8, message = TOKEN_SIZE_MESSAGE)
    private String token;

    @NotBlank(message = NOT_BLANK_MESSAGE)
    @Size(min = 7, max = 64, message = PASSWORD_SIZE_MESSAGE)
    private String password;

    @NotBlank(message = NOT_BLANK_MESSAGE)
    @Size(min = 7, max = 64, message = PASSWORD_SIZE_MESSAGE)
    @JsonProperty("confirm_password")
    private String confirmPassword;
}
