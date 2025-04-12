package com.winworld.coursestools.dto.user;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import static com.winworld.coursestools.constants.ValidationMessages.NOT_NULL_MESSAGE;

@Data
public class UpdateUserPasswordDto {
    private static final String PASSWORD_SIZE_MESSAGE = "Size must be between 7 and 64 characters";

    @Size(min = 7, max = 64, message = PASSWORD_SIZE_MESSAGE)
    @NotNull(message = NOT_NULL_MESSAGE)
    private String password;
    @Size(min = 7, max = 64, message = PASSWORD_SIZE_MESSAGE)
    @NotNull(message = NOT_NULL_MESSAGE)
    private String newPassword;
    @NotNull(message = NOT_NULL_MESSAGE)
    @Size(min = 7, max = 64, message = PASSWORD_SIZE_MESSAGE)
    private String newPasswordConfirm;
}
