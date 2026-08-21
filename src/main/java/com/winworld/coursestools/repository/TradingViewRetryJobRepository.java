package com.winworld.coursestools.repository;

import com.winworld.coursestools.entity.TradingViewRetryJob;
import com.winworld.coursestools.enums.TradingViewRetryJobStatus;
import com.winworld.coursestools.enums.TradingViewRetryJobType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static jakarta.persistence.LockModeType.PESSIMISTIC_WRITE;

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

    boolean existsByUserIdAndTypeAndStatus(
            Integer userId,
            TradingViewRetryJobType type,
            TradingViewRetryJobStatus status
    );

    @Modifying
    @Query(value = """
            INSERT INTO trading_view_retry_jobs (
                user_id, type, status, payload, command_id, next_attempt_at,
                attempts, last_error, first_enqueued_at, force_retry_count,
                created_at, updated_at
            ) VALUES (
                :userId, 'ACTIVATE', 'PENDING', CAST(:payload AS jsonb), :commandId,
                :now, 0, NULL, :now, 0, :now, :now
            )
            ON CONFLICT (user_id, type) WHERE status = 'PENDING'
            DO UPDATE SET
                payload = EXCLUDED.payload,
                command_id = EXCLUDED.command_id,
                next_attempt_at = EXCLUDED.next_attempt_at,
                attempts = 0,
                last_error = NULL,
                updated_at = EXCLUDED.updated_at
            """, nativeQuery = true)
    void stagePendingActivation(
            @Param("userId") Integer userId,
            @Param("payload") String payload,
            @Param("commandId") String commandId,
            @Param("now") LocalDateTime now
    );

    @Lock(PESSIMISTIC_WRITE)
    @Query("""
            SELECT j FROM TradingViewRetryJob j
            WHERE j.user.id = :userId
              AND j.type = :type
              AND j.status = :status
            """)
    Optional<TradingViewRetryJob> findByUserIdAndTypeAndStatusForUpdate(
            @Param("userId") Integer userId,
            @Param("type") TradingViewRetryJobType type,
            @Param("status") TradingViewRetryJobStatus status
    );

    @Modifying
    @Query("""
            DELETE FROM TradingViewRetryJob j
            WHERE j.user.id = :userId
              AND j.type = :type
              AND j.status = :status
            """)
    int deleteByUserIdAndTypeAndStatus(
            @Param("userId") Integer userId,
            @Param("type") TradingViewRetryJobType type,
            @Param("status") TradingViewRetryJobStatus status
    );

    // Atomic admin force-retry: resets attempts, bumps force_retry_count, schedules now,
    // clears last_error. One UPDATE — no read-modify-write race with the scheduler.
    @Modifying
    @Query("""
            UPDATE TradingViewRetryJob j
               SET j.status = :pending,
                   j.nextAttemptAt = :now,
                   j.lastError = null,
                   j.attempts = 0,
                   j.forceRetryCount = j.forceRetryCount + 1
             WHERE j.id = :id
            """)
    int forceRetry(
            @Param("id") Integer id,
            @Param("pending") TradingViewRetryJobStatus pending,
            @Param("now") LocalDateTime now
    );

    @Modifying
    @Query("DELETE FROM TradingViewRetryJob j WHERE j.id = :id")
    int deleteByIdReturning(@Param("id") Integer id);
}
