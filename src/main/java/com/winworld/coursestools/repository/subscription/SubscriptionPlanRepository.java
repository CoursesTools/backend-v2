package com.winworld.coursestools.repository.subscription;

import com.winworld.coursestools.entity.subscription.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Integer> {

    @Query("SELECT sp FROM SubscriptionPlan sp JOIN FETCH sp.subscriptionType WHERE sp.id = :id")
    Optional<SubscriptionPlan> findByIdJoinSubscriptionType(Integer id);
}
