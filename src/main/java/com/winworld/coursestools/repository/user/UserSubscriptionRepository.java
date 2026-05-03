package com.winworld.coursestools.repository.user;

import com.winworld.coursestools.dto.subscription.PlanSubscriptionCount;
import com.winworld.coursestools.dto.subscription.TierPlanSubscriptionCount;
import com.winworld.coursestools.entity.user.UserSubscription;
import com.winworld.coursestools.enums.Plan;
import com.winworld.coursestools.enums.SubscriptionStatus;

import java.util.Collection;
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
            ORDER BY us.expired_at DESC, us.updated_at DESC, us.id DESC
            LIMIT 1
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
            JOIN FETCH us.user u
            LEFT JOIN FETCH u.referred
            WHERE us.expiredAt <= CURRENT_TIMESTAMP
            AND us.status = :status
            """)
    List<UserSubscription> findAllWithExpiredSubscriptionsByStatus(SubscriptionStatus status);

    @Query(value = """
            SELECT us
            FROM UserSubscription us
            JOIN FETCH us.user u
            WHERE us.expiredAt <= CURRENT_TIMESTAMP
            AND us.status IN ('GRANTED')
            AND us.isTrial = true
            """)
    List<UserSubscription> findAllWithExpiredTrialSubscription();

    @Query(value = """
            SELECT us
            FROM UserSubscription us
            JOIN FETCH us.user u
            WHERE us.expiredAt <= :cutoffDate
              AND us.status = :status
            """)
    List<UserSubscription> findExpiredSubscriptionsOlderThanDays(LocalDateTime cutoffDate, SubscriptionStatus status);

    @Query(value = """
            SELECT us
            FROM UserSubscription us
            JOIN FETCH us.plan p
            WHERE us.user.id = :userId
              AND p.subscriptionType.id = :subscriptionTypeId
              AND us.status <> 'TERMINATED'
            ORDER BY us.expiredAt DESC, us.updatedAt DESC, us.id DESC
            """)
    List<UserSubscription> findAllCurrentBySubTypeNotTerminatedWithPlan(int subscriptionTypeId, int userId);

    @Query(value = """
            SELECT us
            FROM UserSubscription us
            JOIN FETCH us.user u
            LEFT JOIN FETCH u.referred
            JOIN FETCH us.plan p
            WHERE us.isTrial = false
              AND us.status <> 'TERMINATED'
              AND us.expiredAt <= :cutoffDate
              AND p.name IN :plans
            ORDER BY us.expiredAt ASC, us.id ASC
            """)
    List<UserSubscription> findAllNonTerminatedPastGracePeriod(LocalDateTime cutoffDate, Collection<Plan> plans);

    @Query(value = """
            SELECT COUNT(us)
            FROM UserSubscription us
            JOIN us.plan p
            WHERE us.isTrial = false
              AND us.status <> 'TERMINATED'
              AND us.expiredAt <= :cutoffDate
              AND p.name IN :plans
            """)
    long countAllNonTerminatedPastGracePeriod(LocalDateTime cutoffDate, Collection<Plan> plans);

    @Query(value = """
            SELECT us
            FROM UserSubscription us
            JOIN FETCH us.user u
            LEFT JOIN FETCH u.referred
            WHERE us.id = :id
            """)
    Optional<UserSubscription> findByIdWithUserDetails(int id);

    @Query(value = """
            SELECT us.*
            FROM users_subscriptions us
            WHERE us.payment_provider_data ->> 'subscriptionId' = :subscriptionId
            AND us.payment_method = 'STRIPE'
            ORDER BY us.updated_at DESC, us.id DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<UserSubscription> findByStripeSubscriptionId(String subscriptionId);

    @Query("""
                SELECT us.plan.name AS plan, COUNT(us) AS count
                FROM UserSubscription us
                WHERE us.createdAt <= :targetDate
                  AND us.expiredAt >= :targetDate
                GROUP BY us.plan.name
            """)
    List<PlanSubscriptionCount> countActiveSubscriptionsOnDate(LocalDateTime targetDate);

    @Query("""
                SELECT us.plan.tier AS tier, us.plan.name AS plan, COUNT(us) AS count
                FROM UserSubscription us
                WHERE us.status IN :statuses
                  AND us.expiredAt > CURRENT_TIMESTAMP
                  AND us.isTrial = false
                GROUP BY us.plan.tier, us.plan.name
            """)
    List<TierPlanSubscriptionCount> countActiveSubscriptionsByTierAndPlan(
            Collection<SubscriptionStatus> statuses
    );
}
