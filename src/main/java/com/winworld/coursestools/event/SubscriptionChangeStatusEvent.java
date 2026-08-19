package com.winworld.coursestools.event;

import com.winworld.coursestools.enums.SubscriptionEventType;
import com.winworld.coursestools.enums.TradingViewExpirationPolicy;
import lombok.Data;

@Data
public class SubscriptionChangeStatusEvent {
    private String email;
    private String tradingViewUsername;
    private int userSubscriptionId;
    private SubscriptionEventType eventType;
    private TradingViewExpirationPolicy tradingViewExpirationPolicy;
}
