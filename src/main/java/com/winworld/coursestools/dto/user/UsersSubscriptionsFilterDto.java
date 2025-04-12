package com.winworld.coursestools.dto.user;

import com.winworld.coursestools.enums.SubscriptionName;
import com.winworld.coursestools.enums.SubscriptionStatus;
import lombok.Data;

import java.util.List;

@Data
public class UsersSubscriptionsFilterDto {
    private List<SubscriptionStatus> statuses;
    private List<SubscriptionName> names;
    private Boolean isTrial;
}
