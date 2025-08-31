package com.winworld.coursestools.repository.user;

import com.winworld.coursestools.entity.user.UserAlert;
import com.winworld.coursestools.entity.user.UserAlertId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserAlertRepository extends JpaRepository<UserAlert, UserAlertId>, JpaSpecificationExecutor<UserAlert> {

    @Query("""
        select ua.id
        from UserAlert ua
        where ua.id.userId = :userId
          and ua.id.alertId in :alertIds
    """)
    List<UserAlertId> findIdsByUserIdAndAlertIds(int userId, List<Integer> alertIds);

    void deleteAllByUser_Id(int userId);
}