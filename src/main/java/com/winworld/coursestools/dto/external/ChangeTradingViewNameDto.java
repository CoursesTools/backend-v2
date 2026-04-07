package com.winworld.coursestools.dto.external;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.winworld.coursestools.enums.SubscriptionTier;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class ChangeTradingViewNameDto {
    @JsonProperty("old")
    private String oldName;
    @JsonProperty("new")
    private String newName;
    private SubscriptionTier tier;
    private LocalDateTime expiration;
}
