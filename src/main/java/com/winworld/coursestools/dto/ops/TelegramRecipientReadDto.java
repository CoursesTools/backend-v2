package com.winworld.coursestools.dto.ops;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class TelegramRecipientReadDto {
    @Schema(requiredMode = REQUIRED)
    private Integer id;
    @Schema(requiredMode = REQUIRED, description = "Telegram user id (string to keep it exact in JavaScript)")
    private String telegramId;
    private String label;
    @Schema(requiredMode = REQUIRED, description = "UTC")
    private LocalDateTime createdAt;
}
