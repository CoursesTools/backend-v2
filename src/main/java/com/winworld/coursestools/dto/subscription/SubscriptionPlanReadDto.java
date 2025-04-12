package com.winworld.coursestools.dto.subscription;

import com.winworld.coursestools.enums.Plan;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

@Data
public class SubscriptionPlanReadDto {
    @Schema(requiredMode = REQUIRED)
    private int id;
    @Schema(requiredMode = REQUIRED)
    private Plan name;
    @Schema(requiredMode = REQUIRED)
    private float price;
    @Schema(requiredMode = REQUIRED)
    private float discountMultiplier;
}
