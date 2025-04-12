package com.winworld.coursestools.repository.user;

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

    @Query("SELECT COUNT(u) > 0 FROM User u WHERE u.telegramId = :telegramId AND u.telegramId IS NOT NULL")
    boolean existsByTelegramId(String telegramId);

    @Query("SELECT COUNT(u) > 0 FROM User u WHERE u.profile.discordId = :discordId AND u.profile.discordId IS NOT NULL")
    boolean existsByProfileDiscordId(String discordId);

    @Query("SELECT u FROM User u WHERE u.partnerCode.code = :partnerCode")
    Optional<User> findUserByPartnerCode(String partnerCode);
}
