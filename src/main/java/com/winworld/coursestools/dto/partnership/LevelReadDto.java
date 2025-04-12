package com.winworld.coursestools.dto.partnership;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

@Data
@Builder
public class LevelReadDto {
    @Schema(requiredMode = REQUIRED)
    private int rank;
    @Schema(requiredMode = REQUIRED)
    private String name;
    @Schema(requiredMode = REQUIRED)
    private int requiredReferrals;
    @Schema(requiredMode = REQUIRED)
    private BigDecimal cashback1;
    @Schema(requiredMode = REQUIRED)
    private BigDecimal cashback2;
}
