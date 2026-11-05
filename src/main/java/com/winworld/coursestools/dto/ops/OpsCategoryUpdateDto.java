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
public class OpsCategoryUpdateDto {
    @NotNull
    @Schema(requiredMode = REQUIRED, description = "false stops Telegram delivery for this category (still saved to history)")
    private Boolean enabled;
}
