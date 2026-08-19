package com.winworld.coursestools.event;

import com.winworld.coursestools.enums.SubscriptionEventType;
import com.winworld.coursestools.enums.SubscriptionTier;
import com.winworld.coursestools.enums.TradingViewExpirationPolicy;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SubscriptionChangeStatusEvent {
    private String email;
    private String tradingViewUsername;
    private int userId;
    private int userSubscriptionId;
    private SubscriptionEventType eventType;
    private TradingViewExpirationPolicy tradingViewExpirationPolicy;
    private LocalDateTime expiration;
    private SubscriptionTier tier;
    private boolean lifetime;
    private String activationCommandId;
}
