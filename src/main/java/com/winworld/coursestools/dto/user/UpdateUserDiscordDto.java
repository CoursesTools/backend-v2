package com.winworld.coursestools.dto.user;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import static com.winworld.coursestools.constants.ValidationMessages.NOT_NULL_MESSAGE;

@Data
public class UpdateUserDiscordDto {
    @NotNull(message = NOT_NULL_MESSAGE)
    private String discordAuthCode;
}
