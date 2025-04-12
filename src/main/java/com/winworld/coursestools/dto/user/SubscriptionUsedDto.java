package com.winworld.coursestools.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

@Data
@Builder
public class SubscriptionUsedDto {
    @Schema(requiredMode = REQUIRED)
    private boolean used;
}
