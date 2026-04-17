package com.winworld.coursestools.repository.user;

import com.winworld.coursestools.entity.user.UserSocial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserSocialRepository extends JpaRepository<UserSocial, Integer> {
    // TradingView treats usernames as case-insensitive ("TesT" == "test"); we mirror
    // that in every lookup so admins can type any casing. Writes lowercase at bind time.
    boolean existsByTradingViewNameIgnoreCase(String tradingViewName);

    boolean existsByTelegramId(String telegramId);

    boolean existsByDiscordId(String discordId);

    Optional<UserSocial> findByTradingViewNameIgnoreCase(String tradingViewName);
}
