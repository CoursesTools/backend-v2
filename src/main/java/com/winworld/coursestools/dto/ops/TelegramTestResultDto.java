package com.winworld.coursestools.dto.ops;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class TelegramTestResultDto {
    @Schema(requiredMode = REQUIRED)
    private String telegramId;
    private String label;
    @Schema(requiredMode = REQUIRED, description = "SENT, UNREACHABLE (recipient must press Start in the bot), FAILED or NOT_CONFIGURED")
    private String status;
    private String detail;
}
