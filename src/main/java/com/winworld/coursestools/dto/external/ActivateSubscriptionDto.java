package com.winworld.coursestools.dto.external;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ActivateSubscriptionDto {
    private String email;
    @JsonProperty(value = "tv")
    private String tradingViewName;
    private String secret;
    private LocalDateTime expiration;

    public ActivateSubscriptionDto(String email, String tradingViewName, LocalDateTime expiration) {
        this.email = email;
        this.tradingViewName = tradingViewName;
        this.expiration = expiration;
    }
}
