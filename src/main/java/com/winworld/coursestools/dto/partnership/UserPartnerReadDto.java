package com.winworld.coursestools.dto.partnership;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public interface UserPartnerReadDto {
    @Schema(requiredMode = REQUIRED)
    String getEmail();
    Boolean getIsActive();
    @Schema(requiredMode = REQUIRED)
    LocalDateTime getActiveUpdatedAt();
    @Schema(requiredMode = REQUIRED)
    LocalDateTime getCreatedAt();
    String getDiscordId();
    @Schema(requiredMode = REQUIRED)
    BigDecimal getProfit();
    @Schema(requiredMode = REQUIRED)
    Integer getReferralsCount();
    @Schema(requiredMode = REQUIRED)
    Integer getActiveReferralsCount();
}
