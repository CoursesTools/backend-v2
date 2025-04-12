package com.winworld.coursestools.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import static com.winworld.coursestools.constants.ValidationMessages.NOT_BLANK_MESSAGE;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GoogleAuthSignInDto {
    @NotBlank(message = NOT_BLANK_MESSAGE)
    private String authorizationCode;
}
