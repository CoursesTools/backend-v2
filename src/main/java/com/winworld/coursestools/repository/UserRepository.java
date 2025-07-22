package com.winworld.coursestools.repository;

import com.winworld.coursestools.entity.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findUserByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByTradingViewName(String tradingViewName);

    @Query("""
            SELECT u FROM User u
            WHERE u.id = (
                SELECT pc.owner.id FROM PromoCode pc
                WHERE pc.code = :code
                         )
            """)
    Optional<User> findUserByPartnerCode(String code);

    int countByReferrerId(int referrerId);
}
