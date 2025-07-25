package com.winworld.coursestools.dto.external;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class ActivateSubscriptionDto {
    private String email;
    @JsonProperty(value = "tv")
    private String tradingViewName;
    private LocalDateTime expiration;
}
