package com.winworld.coursestools.dto.subscription;

import com.winworld.coursestools.enums.Plan;
import com.winworld.coursestools.enums.SubscriptionTier;

public interface TierPlanSubscriptionCount {
    SubscriptionTier getTier();
    Plan getPlan();
    Integer getCount();
}
