package com.winworld.coursestools.dto.subscription;

import com.winworld.coursestools.enums.Plan;

public interface PlanSubscriptionCount {
    Plan getPlan();
    Integer getCount();
}
