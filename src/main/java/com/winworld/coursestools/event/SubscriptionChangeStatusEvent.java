package com.winworld.coursestools.event;

import com.winworld.coursestools.enums.SubscriptionEventType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SubscriptionChangeStatusEvent {
    private String email;
    private String tradingViewUsername;
    private int userSubscriptionId;
    private SubscriptionEventType eventType;
}
