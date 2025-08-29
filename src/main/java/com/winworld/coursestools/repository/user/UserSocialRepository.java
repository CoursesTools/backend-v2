package com.winworld.coursestools.repository.user;

import com.winworld.coursestools.entity.user.UserSocial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserSocialRepository extends JpaRepository<UserSocial, Integer> {
    boolean existsByTradingViewName(String tradingViewName);

    boolean existsByTelegramId(String telegramId);

    boolean existsByDiscordId(String discordId);

    Optional<UserSocial> findByTradingViewName(String tradingViewName);
}
