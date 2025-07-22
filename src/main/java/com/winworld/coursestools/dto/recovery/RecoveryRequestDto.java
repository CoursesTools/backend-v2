package com.winworld.coursestools.dto.recovery;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RecoveryRequestDto {
    private static final String NOT_BLANK_MESSAGE = "The field must not be empty";
    private static final String EMAIL_MESSAGE = "Field is not email";

    @NotBlank(message = NOT_BLANK_MESSAGE)
    @Email(message = EMAIL_MESSAGE)
    private String email;
}
