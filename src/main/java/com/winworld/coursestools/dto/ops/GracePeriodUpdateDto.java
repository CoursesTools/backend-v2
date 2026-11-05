package com.winworld.coursestools.dto.ops;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GracePeriodUpdateDto {
    @NotNull
    @Min(1)
    @Max(30)
    @Schema(requiredMode = REQUIRED, minimum = "1", maximum = "30")
    private Integer gracePeriodDays;

    @NotNull
    @Schema(requiredMode = REQUIRED, description = "The settings version the change was made against")
    private Integer version;
}
