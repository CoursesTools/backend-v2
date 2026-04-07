package com.winworld.coursestools.repository;

import com.winworld.coursestools.entity.TierIndicatorPermission;
import com.winworld.coursestools.enums.SubscriptionTier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TierIndicatorPermissionRepository extends JpaRepository<TierIndicatorPermission, Integer> {
    List<TierIndicatorPermission> findByTierAndSubscriptionType_Id(SubscriptionTier tier, int subscriptionTypeId);
    boolean existsByTier(SubscriptionTier tier);
}
