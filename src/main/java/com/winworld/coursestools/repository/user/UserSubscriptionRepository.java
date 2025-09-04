package com.winworld.coursestools.repository.user;

import com.winworld.coursestools.dto.subscription.PlanSubscriptionCount;
import com.winworld.coursestools.entity.user.UserSubscription;
import com.winworld.coursestools.enums.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, Integer>, JpaSpecificationExecutor<UserSubscription> {
    @Query(value = """
            SELECT us.*
            FROM users_subscriptions AS us
            JOIN subscription_plans AS sp
            ON us.plan_id = sp.id
            WHERE us.user_id = :userId
            AND sp.subscription_type_id = :subscriptionTypeId
            AND us.status NOT IN ('TERMINATED')
            """, nativeQuery = true)
    Optional<UserSubscription> getUserSubBySubTypeNotTerminated(int subscriptionTypeId, int userId);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM users_subscriptions AS us
                JOIN subscription_plans AS sp
                ON us.plan_id = sp.id
                WHERE us.user_id = :userId
                AND sp.subscription_type_id = :subscriptionTypeId
            )
            """, nativeQuery = true)
    boolean hasEverHadSubscriptionOfType(int subscriptionTypeId, int userId);

    @Query(value = """
            SELECT us
            FROM UserSubscription us
            WHERE us.expiredAt <= CURRENT_TIMESTAMP
            AND us.status = :status
            """)
    List<UserSubscription> findAllWithExpiredSubscriptionsByStatus(SubscriptionStatus status);

    @Query(value = """
            SELECT us
            FROM UserSubscription us
            WHERE us.expiredAt <= CURRENT_TIMESTAMP
            AND us.status IN ('GRANTED')
            AND us.isTrial = true
            """)
    List<UserSubscription> findAllWithExpiredTrialSubscription();

    @Query(value = """
            SELECT us
            FROM UserSubscription us
            WHERE us.expiredAt <= :cutoffDate
              AND us.status = :status
            """)
    List<UserSubscription> findExpiredSubscriptionsOlderThanDays(LocalDateTime cutoffDate, SubscriptionStatus status);

    @Query("""
                SELECT us.plan.name AS plan, COUNT(us) AS count
                FROM UserSubscription us
                WHERE us.createdAt <= :targetDate
                  AND us.expiredAt >= :targetDate
                GROUP BY us.plan.name
            """)
    List<PlanSubscriptionCount> countActiveSubscriptionsOnDate(LocalDateTime targetDate);
}