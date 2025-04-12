package com.winworld.coursestools.repository.user;

import com.winworld.coursestools.entity.user.UserAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserAlertRepository extends JpaRepository<UserAlert, Integer>, JpaSpecificationExecutor<UserAlert> {

    @Query("""
            SELECT ua FROM UserAlert ua
            WHERE ua.user.id = :userId
            AND ua.alert.id IN :alertsIds
            """)
    List<UserAlert> findByUserIdAndAlertsIds(int userId, List<Integer> alertsIds);
}