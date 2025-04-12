package com.winworld.coursestools.dto.alert;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public interface AlertCategoriesReadDto {
    @Schema(requiredMode = REQUIRED)
    List<String> getTypes();
    @Schema(requiredMode = REQUIRED)
    List<String> getBrokers();
    @Schema(requiredMode = REQUIRED)
    List<String> getTimeFrames();
    @Schema(requiredMode = REQUIRED)
    List<String> getEvents();
    @Schema(requiredMode = REQUIRED)
    List<String> getAssets();
    @Schema(requiredMode = REQUIRED)
    List<String> getIndicators();
}
