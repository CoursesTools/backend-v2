package com.winworld.coursestools.repository;

import com.winworld.coursestools.dto.alert.AlertCategoriesReadDto;
import com.winworld.coursestools.entity.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Map;

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

    @Query(value = """
        SELECT DISTINCT a.type
        FROM alerts a
        WHERE multi_alert = :isMulti
    """, nativeQuery = true)
    List<String> getAllTypes(boolean isMulti);

    @Query(value = """
        SELECT DISTINCT a.asset as assets
        FROM alerts a
        WHERE a.type = :type AND a.multi_alert = :isMulti
    """, nativeQuery = true)
    List<String> getAllAssetsByType(String type, boolean isMulti);

    @Query(value = """
        SELECT DISTINCT a.broker
        FROM alerts a
        WHERE a.type = :type AND a.multi_alert = :isMulti
    """, nativeQuery = true)
    List<String> getAllBrokersByType(String type, boolean isMulti);

    @Query(value = """
        SELECT DISTINCT a.tf
        FROM alerts a
        WHERE multi_alert = :isMulti
    """, nativeQuery = true)
    List<String> getAllTimeFrames(boolean isMulti);

    @Query(value = """
        SELECT DISTINCT a.event
        FROM alerts a
        WHERE multi_alert = :isMulti
    """, nativeQuery = true)
    List<String> getAllEvents(boolean isMulti);
}
