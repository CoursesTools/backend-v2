package com.winworld.coursestools.dto.subscription;

import com.winworld.coursestools.enums.SubscriptionName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

@Data
public class SubscriptionReadDto {
    @Schema(requiredMode = REQUIRED)
    private int id;
    @Schema(requiredMode = REQUIRED)
    private SubscriptionName name;
    @Schema(requiredMode = REQUIRED)
    private String displayName;
    @Schema(requiredMode = REQUIRED)
    private List<SubscriptionPlanReadDto> plans;
}
