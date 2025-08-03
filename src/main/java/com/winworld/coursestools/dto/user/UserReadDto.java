package com.winworld.coursestools.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

@Data
@AllArgsConstructor
@Builder
public class UserReadDto {
    @Schema(requiredMode = REQUIRED)
    private Long id;
    @Schema(requiredMode = REQUIRED)
    private String email;
    @Schema(requiredMode = REQUIRED)
    private String tradingViewName;
    @Schema(requiredMode = REQUIRED)
    private LocalDateTime createdAt;
    @Schema(requiredMode = REQUIRED)
    private Boolean isActive;
    @Schema(requiredMode = NOT_REQUIRED)
    private Boolean isReferralBonusUsed;
    @Schema(requiredMode = REQUIRED)
    private String telegramId;
    @Schema(requiredMode = REQUIRED)
    private String discordId;
    @Schema(requiredMode = REQUIRED)
    private BigDecimal balance;
}
