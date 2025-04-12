package com.winworld.coursestools.repository;

import com.winworld.coursestools.dto.alert.AlertCategoriesReadDto;
import com.winworld.coursestools.entity.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlertRepository extends JpaRepository<Alert, Integer>, JpaSpecificationExecutor<Alert> {
    @Query(value = """
        SELECT
            array_agg(DISTINCT a.type) as types,
            array_agg(DISTINCT a.asset) as assets,
            array_agg(DISTINCT a.broker) as brokers,
            array_agg(DISTINCT a.tf) as timeFrames,
            array_agg(DISTINCT a.event) as events,
            array_agg(DISTINCT a.indicator) as indicators
        FROM alerts a
        JOIN users_alerts ua ON a.id = ua.alert_id
        WHERE ua.user_id = :userId
    """, nativeQuery = true)
    AlertCategoriesReadDto getUserAlertsCategories(@Param("userId") Integer userId);
}
