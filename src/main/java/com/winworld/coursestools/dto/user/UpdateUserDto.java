package com.winworld.coursestools.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.openapitools.jackson.nullable.JsonNullable;

import static com.winworld.coursestools.constants.RegularExpression.TRADING_VIEW_USERNAME;
import static com.winworld.coursestools.constants.ValidationMessages.PATTERN_MESSAGE;

@Data
public class UpdateUserDto {
    private static final String TELEGRAM_ID_MESSAGE = "Telegram ID must be 10 characters long";
    private static final String DISCORD_ID_MESSAGE = "Discord ID must be between 17 and 32 characters long";
    private static final String PARTNER_CODE_MESSAGE = "Partner code must be between 3 and 32 characters long";

    @Size(min = 3, max = 32, message = PARTNER_CODE_MESSAGE)
    private String partnerCode;
    private Boolean termsAccepted;

    @Size(max = 10, min = 10, message = TELEGRAM_ID_MESSAGE)
    @Schema(type = "string", nullable = true)
    private JsonNullable<String> telegramId;

    @Size(max = 32, min = 17, message = DISCORD_ID_MESSAGE)
    @Schema(type = "string", nullable = true)
    private JsonNullable<String> discordId;

    @Pattern(regexp = TRADING_VIEW_USERNAME, message = PATTERN_MESSAGE)
    private String tradingViewName;

}
