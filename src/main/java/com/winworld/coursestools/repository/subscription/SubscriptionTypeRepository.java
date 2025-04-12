package com.winworld.coursestools.repository.subscription;

import com.winworld.coursestools.entity.subscription.SubscriptionType;
import com.winworld.coursestools.enums.SubscriptionName;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SubscriptionTypeRepository extends JpaRepository<SubscriptionType, Integer> {
    Optional<SubscriptionType> findByName(SubscriptionName name);
}
