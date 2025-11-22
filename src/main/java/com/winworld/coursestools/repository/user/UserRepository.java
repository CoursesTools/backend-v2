package com.winworld.coursestools.repository.user;

import com.winworld.coursestools.entity.user.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findUserByEmail(String email);

    boolean existsByEmail(String email);

    @Query("SELECT u FROM User u WHERE u.partnerCode.code = :partnerCode")
    Optional<User> findUserByPartnerCode(String partnerCode);

    @EntityGraph(attributePaths = {"profile", "partnership", "social", "subscriptions", "referred", "finance"})
    @Query("SELECT u FROM User u WHERE LOWER(u.email) = LOWER(:email)")
    Optional<User> findUserWithDetailsByEmail(String email);

    @EntityGraph(attributePaths = {"profile", "partnership", "social", "subscriptions", "referred", "finance"})
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findUserWithDetailsById(Integer id);

    @EntityGraph(attributePaths = {"profile", "partnership", "social", "subscriptions", "referred", "finance"})
    @Query("SELECT u FROM User u JOIN u.social s WHERE LOWER(s.tradingViewName) = LOWER(:tradingViewName)")
    Optional<User> findUserWithDetailsByTradingViewName(String tradingViewName);

}
