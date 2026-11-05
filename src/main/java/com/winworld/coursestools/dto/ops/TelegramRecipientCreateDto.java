package com.winworld.coursestools.dto.ops;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TelegramRecipientCreateDto {
    @NotBlank
    @Pattern(regexp = "^[1-9][0-9]{4,14}$", message = "must be a numeric Telegram user id")
    @Schema(requiredMode = REQUIRED, example = "639072017")
    private String telegramId;

    @Size(max = 64)
    @Pattern(regexp = "^[^<>]*$", message = "must not contain < or >")
    private String label;
}
