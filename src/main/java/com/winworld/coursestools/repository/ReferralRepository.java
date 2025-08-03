package com.winworld.coursestools.repository;

import com.winworld.coursestools.dto.partnership.LevelEarningDto;
import com.winworld.coursestools.dto.partnership.UserPartnerReadDto;
import com.winworld.coursestools.entity.Referral;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ReferralRepository extends JpaRepository<Referral, Long> {
    int countByReferrerIdAndIsActive(int referrerId, boolean isActive);

    @Query(value = """
            SELECT
                re.cashback_level AS cashbackLevel,
                COALESCE(SUM(re.amount), 0) AS earnings
            FROM referrals_earnings re
            JOIN referrals r ON re.referral_id = r.id
            WHERE r.referrer_id = :userId
            GROUP BY re.cashback_level
            ORDER BY re.cashback_level
            """, nativeQuery = true)
    List<LevelEarningDto> calculateLevelEarnings(int userId);

    @Query(value = """
            INSERT INTO referrals_earnings (referral_id, amount, cashback_level, transaction_id)
            VALUES (:referralId, :amount, :cashbackLevel, :transactionId)
            """, nativeQuery = true)
    @Modifying
    void addReferralEarning(int referralId, int amount, int cashbackLevel, int transactionId);

    @Query(value = """
        SELECT * FROM (
            SELECT
                r.is_active as isActive,
                COALESCE(SUM(re.amount), 0) as profit,
                u.email as email,
                u.created_at as createdAt,
                r.updated_at as activeUpdatedAt,
                us.discord_id as discordId,
                (SELECT COUNT(*) FROM referrals r2 WHERE r2.referrer_id = r.referred_id) as referralsCount,
                (SELECT COUNT(*) FROM referrals r2 WHERE r2.referrer_id = r.referred_id AND r2.is_active = true) as activeReferralsCount
            FROM referrals r
                LEFT JOIN users u ON u.id = r.referred_id
                JOIN user_socials us ON us.user_id = u.id
                LEFT JOIN referrals_earnings re ON re.referral_id = r.id
            WHERE r.referrer_id = :userId
            GROUP BY r.id, u.id, us.user_id
    ) sub
    """, countQuery = """
        SELECT COUNT(*)
        FROM referrals r
        WHERE r.referrer_id = :userId
    """,nativeQuery = true)
    Page<UserPartnerReadDto> findUserPartnersByReferrerId(int userId, Pageable pageable);
}
