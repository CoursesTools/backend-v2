package com.winworld.coursestools.repository;

import com.winworld.coursestools.entity.TradingViewRetryJob;
import com.winworld.coursestools.enums.TradingViewRetryJobStatus;
import com.winworld.coursestools.enums.TradingViewRetryJobType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TradingViewRetryJobRepository
        extends JpaRepository<TradingViewRetryJob, Integer>,
                JpaSpecificationExecutor<TradingViewRetryJob> {

    @Query(value = """
            SELECT * FROM trading_view_retry_jobs
             WHERE status = 'PENDING' AND next_attempt_at <= :now
             ORDER BY next_attempt_at
             LIMIT :limit
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<TradingViewRetryJob> findDueForUpdate(
            @Param("now") LocalDateTime now,
            @Param("limit") int limit
    );

    Optional<TradingViewRetryJob> findByUserIdAndTypeAndStatus(
            Integer userId,
            TradingViewRetryJobType type,
            TradingViewRetryJobStatus status
    );
}
