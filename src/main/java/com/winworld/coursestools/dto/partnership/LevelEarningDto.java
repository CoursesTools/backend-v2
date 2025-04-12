package com.winworld.coursestools.dto.partnership;

import io.swagger.v3.oas.annotations.media.Schema;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public interface LevelEarningDto {
    @Schema(requiredMode = REQUIRED)
    int getCashbackLevel();

    @Schema(requiredMode = REQUIRED)
    int getEarnings();
}
