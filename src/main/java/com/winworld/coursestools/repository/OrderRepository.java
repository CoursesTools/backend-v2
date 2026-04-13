package com.winworld.coursestools.repository;

import com.winworld.coursestools.dto.order.TierPlanOrderCount;
import com.winworld.coursestools.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Integer>, JpaSpecificationExecutor<Order> {
    Page<Order> findByUserIdOrderByCreatedAtDesc(int userId, Pageable pageable);

    @Query("""
                SELECT o.plan.tier AS tier, o.plan.name AS plan, COUNT(o) AS count
                FROM Order o
                WHERE o.status = 'PAID'
                  AND o.createdAt >= :start
                  AND o.createdAt < :end
                  AND o.plan.name <> 'TRIAL'
                GROUP BY o.plan.tier, o.plan.name
            """)
    List<TierPlanOrderCount> countPaidOrdersByTierAndPlan(LocalDateTime start, LocalDateTime end);
}
