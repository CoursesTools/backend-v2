package com.winworld.coursestools.dto.alert;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

@Data
public class AlertSubscriptionCategoriesDto {
    @Schema(requiredMode = REQUIRED)
    private List<Type> types = new ArrayList<>();
    @Schema(requiredMode = REQUIRED)
    private List<String> events = new ArrayList<>();
    @Schema(requiredMode = REQUIRED)
    private List<String> timeFrames = new ArrayList<>();

    public record Type(@Schema(requiredMode = REQUIRED) String type,
                       @Schema(requiredMode = REQUIRED) List<String> assets,
                       @Schema(requiredMode = REQUIRED) List<String> brokers) {
    }
}
