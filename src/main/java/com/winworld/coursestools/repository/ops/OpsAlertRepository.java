package com.winworld.coursestools.repository.ops;

import com.winworld.coursestools.entity.ops.OpsAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OpsAlertRepository extends JpaRepository<OpsAlert, Long> {
    boolean existsByDedupKey(String dedupKey);

    @Modifying
    @Query("DELETE FROM OpsAlert a WHERE a.createdAt < :cutoff")
    int deleteAllCreatedBefore(@Param("cutoff") LocalDateTime cutoff);

    @Query("""
            SELECT a.category, COUNT(a) FROM OpsAlert a
            WHERE a.createdAt >= :from AND a.createdAt < :to
            GROUP BY a.category
            """)
    List<Object[]> countByCategoryBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
