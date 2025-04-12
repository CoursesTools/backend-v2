package com.winworld.coursestools.repository;

import com.winworld.coursestools.entity.PromoCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PromoCodeRepository extends JpaRepository<PromoCode, String> {

    Optional<PromoCode> findByCode(String code);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM users_promo_codes
                WHERE user_id = :userId
                AND promo_code_id = :promoCodeId
            )
            """, nativeQuery = true)
    boolean existsByUserIdAndPromoCodeId(int userId, int promoCodeId);

    @Modifying
    @Query(value = """
                INSERT INTO users_promo_codes
                (user_id, promo_code_id)
                VALUES (:userId, :promoCodeId)
            """, nativeQuery = true)
    void addUsedPromoCodeForUser(int userId, int promoCodeId);
}
