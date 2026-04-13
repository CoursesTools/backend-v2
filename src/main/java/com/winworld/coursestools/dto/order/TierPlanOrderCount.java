package com.winworld.coursestools.dto.order;

import com.winworld.coursestools.enums.Plan;
import com.winworld.coursestools.enums.SubscriptionTier;

public interface TierPlanOrderCount {
    SubscriptionTier getTier();
    Plan getPlan();
    Integer getCount();
}
