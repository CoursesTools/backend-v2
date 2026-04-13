package com.winworld.coursestools.repository.user;

import com.winworld.coursestools.dto.order.TierPlanOrderCount;
import com.winworld.coursestools.entity.user.UserTransaction;
import com.winworld.coursestools.enums.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface UserTransactionRepository extends JpaRepository<UserTransaction, Integer> {

    @Query("""
        SELECT SUM(ut.amount)
        FROM UserTransaction ut
        WHERE ut.transactionType = :transactionType
    """)
    BigDecimal getTransactionSumAmount(TransactionType transactionType);

    @Query("""
        SELECT SUM(ut.amount)
        FROM UserTransaction ut
        WHERE ut.transactionType = :transactionType
          AND (ut.createdAt >= :start)
    """)
    BigDecimal getTransactionSumAmountFrom(TransactionType transactionType, LocalDateTime start);

    @Query("""
        SELECT SUM(ut.amount)
        FROM UserTransaction ut
        WHERE ut.transactionType = :transactionType
          AND (ut.createdAt <= :end)
    """)
    BigDecimal getTransactionSumAmountTo(TransactionType transactionType, LocalDateTime end);

    @Query("""
        SELECT SUM(ut.amount)
        FROM UserTransaction ut
        WHERE ut.transactionType = :transactionType
          AND (ut.createdAt >= :start)
          AND (ut.createdAt <= :end)
    """)
    BigDecimal getTransactionSumAmountBetween(TransactionType transactionType, LocalDateTime start, LocalDateTime end);

    @Query("""
        SELECT ut.order.plan.tier AS tier,
               ut.order.plan.name AS plan,
               COUNT(ut) AS count
        FROM UserTransaction ut
        WHERE ut.transactionType = 'PURCHASE'
          AND ut.order.status = 'PAID'
          AND ut.createdAt >= :start
          AND ut.createdAt <= :end
        GROUP BY ut.order.plan.tier, ut.order.plan.name
    """)
    List<TierPlanOrderCount> countPurchasesByTierAndPlan(LocalDateTime start, LocalDateTime end);
}
