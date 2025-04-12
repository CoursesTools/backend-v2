package com.winworld.coursestools.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import static com.winworld.coursestools.constants.ValidationMessages.EMAIL_MESSAGE;
import static com.winworld.coursestools.constants.ValidationMessages.NOT_NULL_MESSAGE;

@Data
public class UpdateUserEmailDto {
    private static final String EMAIL_CODE_MESSAGE = "Email code must be 8 characters long";

    @NotNull(message = NOT_NULL_MESSAGE)
    @Email(message = EMAIL_MESSAGE)
    private String email;

    @Size(min = 8, max = 8, message = EMAIL_CODE_MESSAGE)
    private String emailCode;

}
