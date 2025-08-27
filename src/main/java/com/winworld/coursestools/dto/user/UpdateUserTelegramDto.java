package com.winworld.coursestools.dto.user;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import static com.winworld.coursestools.constants.ValidationMessages.NOT_NULL_MESSAGE;

@Data
public class UpdateUserTelegramDto {
    public static final String TOKEN_MUST_BE_EXACTLY_8_CHARACTERS_LONG = "Token must be exactly 8 characters long";
    private static final String TELEGRAM_ID_MESSAGE = "Telegram ID must be 9 characters long";

    @Size(min = 8, max = 8, message = TOKEN_MUST_BE_EXACTLY_8_CHARACTERS_LONG)
    @NotNull(message = NOT_NULL_MESSAGE)
    private String token;
    @Size(min = 9, max = 16, message = TELEGRAM_ID_MESSAGE)
    @NotNull(message = NOT_NULL_MESSAGE)
    private String telegramId;
}
