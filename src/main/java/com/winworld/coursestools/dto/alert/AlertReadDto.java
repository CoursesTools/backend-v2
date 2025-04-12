package com.winworld.coursestools.dto.alert;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

@Data
@Schema(description = "Alert data transfer object with alert details")
public class AlertReadDto {
    @Schema(requiredMode = REQUIRED)
    private int id;
    @Schema(requiredMode = REQUIRED)
    private String type;
    @Schema(requiredMode = REQUIRED)
    private String broker;
    @Schema(requiredMode = REQUIRED)
    private String tf;
    @Schema(requiredMode = REQUIRED)
    private String event;
    @Schema(requiredMode = REQUIRED)
    private String asset;
    @Schema(requiredMode = REQUIRED)
    private String indicator;
    @Schema(requiredMode = REQUIRED)
    private Boolean multiAlert;
}
