package com.winworld.coursestools.dto.partnership;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

@Data
@Builder
public class UserPartnershipReadDto {
    @Schema(requiredMode = REQUIRED)
    private String nextLevelName;
    @Schema(requiredMode = REQUIRED)
    private Integer requiredReferralsForNextLevel;
    @Schema(requiredMode = REQUIRED)
    private Integer inactiveReferralsCount;
    @Schema(requiredMode = REQUIRED)
    private Integer activeReferralsCount;
    @Schema(requiredMode = REQUIRED)
    private String curatorDiscord;
    @Schema(requiredMode = REQUIRED)
    private String currentLevelName;
    @Schema(requiredMode = REQUIRED)
    private List<LevelEarningDto> levelEarnings;
    @Schema(requiredMode = REQUIRED)
    private String partnerCode;
    @Schema(requiredMode = REQUIRED)
    private boolean termsAccepted;
}
