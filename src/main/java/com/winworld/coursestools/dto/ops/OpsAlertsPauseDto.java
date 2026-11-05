package com.winworld.coursestools.dto.ops;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OpsAlertsPauseDto {
    @NotNull
    @Schema(requiredMode = REQUIRED, description = "true drops Telegram delivery (alerts are still saved to history)")
    private Boolean paused;
}
