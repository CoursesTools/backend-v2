package com.winworld.coursestools.dto.ops;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.winworld.coursestools.enums.OpsAlertCategory;
import com.winworld.coursestools.enums.OpsAlertSeverity;
import com.winworld.coursestools.enums.OpsTelegramStatus;
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
public class OpsAlertReadDto {
    @Schema(requiredMode = REQUIRED)
    private Long id;
    @Schema(requiredMode = REQUIRED)
    private OpsAlertCategory category;
    @Schema(requiredMode = REQUIRED)
    private OpsAlertSeverity severity;
    @Schema(requiredMode = REQUIRED)
    private String title;
    @Schema(requiredMode = REQUIRED, description = "Markdown")
    private String body;
    @Schema(requiredMode = REQUIRED)
    private OpsTelegramStatus telegramStatus;
    private String telegramDetail;
    @Schema(requiredMode = REQUIRED, description = "UTC")
    private LocalDateTime createdAt;
}
