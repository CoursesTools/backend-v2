package com.winworld.coursestools.repository;

import com.winworld.coursestools.entity.user.UserSocial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrialActivationRepository extends JpaRepository<UserSocial, Integer> {

    @Query(value = "SELECT EXISTS(SELECT 1 FROM trial_activations WHERE LOWER(tradingview_username) = LOWER(:tradingviewUsername))", nativeQuery = true)
    boolean existsByTradingviewUsername(@Param("tradingviewUsername") String tradingviewUsername);

    @Modifying
    @Query(value = "INSERT INTO trial_activations (user_id, tradingview_username, activation_date) VALUES (:userId, LOWER(:tradingviewUsername), CURRENT_TIMESTAMP)", nativeQuery = true)
    void createTrialActivation(@Param("userId") int userId, @Param("tradingviewUsername") String tradingviewUsername);
}
