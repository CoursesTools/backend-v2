package com.winworld.coursestools.repository;

import com.winworld.coursestools.entity.user.UserSubscription;
import com.winworld.coursestools.enums.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, Integer> {

    @Query(
            """
                    SELECT us FROM UserSubscription us
                    WHERE us.expiredAt < CURRENT_TIMESTAMP
                    AND us.isTrial = false
                    AND us.status = :status
                    """
    )
    List<UserSubscription> findAllWithExpiredSubNotTrial(SubscriptionStatus status);

    @Query(
            """
                    SELECT us FROM UserSubscription us
                    WHERE us.expiredAt < CURRENT_TIMESTAMP
                    AND us.isTrial = true
                    """
    )
    List<UserSubscription> findAllWithExpiredTrialSub();
}
