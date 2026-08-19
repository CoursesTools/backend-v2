package com.winworld.coursestools.dto.admin;

import com.winworld.coursestools.enums.TradingViewDeliveryStatus;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class DirectAccessSubmissionDto {
    Integer subscriptionId;
    String tradingViewName;
    LocalDateTime expiration;
    TradingViewDeliveryStatus deliveryStatus;
}
