package com.winworld.coursestools.dto.recovery;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import static com.winworld.coursestools.constants.ValidationMessages.EMAIL_MESSAGE;
import static com.winworld.coursestools.constants.ValidationMessages.NOT_BLANK_MESSAGE;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RecoveryDto {
    private static final String TOKEN_SIZE_MESSAGE = "Size must be 8 characters";
    private static final String PASSWORD_SIZE_MESSAGE = "Size must be between 7 and 64 characters";

    @Email(message = EMAIL_MESSAGE)
    private String email;

    @Size(min = 8, max = 8, message = TOKEN_SIZE_MESSAGE)
    private String token;

    @Size(min = 7, max = 64, message = PASSWORD_SIZE_MESSAGE)
    private String password;

    @Size(min = 7, max = 64, message = PASSWORD_SIZE_MESSAGE)
    private String confirmPassword;
}
