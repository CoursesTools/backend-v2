package com.winworld.coursestools.service.user;

import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.exception.exceptions.EntityNotFoundException;
import com.winworld.coursestools.repository.user.UserRepository;
import com.winworld.coursestools.repository.user.UserSocialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserDataService {
    private static final String EMAIL_NOT_FOUND = "User with email %s not found";
    private static final String TRADING_VIEW_NAME_NOT_FOUND = "User with trading view name %s not found";
    private static final String ID_NOT_FOUND = "User with id %d not found";
    private static final String PARTNER_CODE_NOT_FOUND = "User with partner code %s not found";

    private final UserRepository userRepository;
    private final UserSocialRepository userSocialRepository;

    public User getUserInfo(String tradingViewName, String email, Integer userId) {
        if (email != null) {
            return userRepository.findUserWithDetailsByEmail(email).orElseThrow(() -> new EntityNotFoundException(
                    String.format(EMAIL_NOT_FOUND, email)
            ));
        }
        else if (tradingViewName != null) {
            return userRepository.findUserWithDetailsByTradingViewName(tradingViewName).orElseThrow(() -> new EntityNotFoundException(
                    String.format(TRADING_VIEW_NAME_NOT_FOUND, tradingViewName)
            ));
        }
        else {
            return userRepository.findUserWithDetailsById(userId).orElseThrow(() -> new EntityNotFoundException(
                    String.format(ID_NOT_FOUND, userId)
            ));
        }
    }

    public User getUserByEmail(String email) {
        return userRepository.findUserByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException(
                        String.format(EMAIL_NOT_FOUND, email)
                ));
    }

    public User getUserByTradingViewName(String username) {
        return userSocialRepository.findByTradingViewName(username)
                .orElseThrow(() -> new EntityNotFoundException(
                        String.format(TRADING_VIEW_NAME_NOT_FOUND, username)
                )).getUser();
    }

    public User getUserById(int id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        String.format(ID_NOT_FOUND, id)
                ));
    }

    public User getUserByPartnerCode(String partnerCode) {
        return userRepository.findUserByPartnerCode(partnerCode)
                .orElseThrow(() -> new EntityNotFoundException(
                        String.format(PARTNER_CODE_NOT_FOUND, partnerCode)
                ));
    }

    public boolean existsByTelegramId(String telegramId) {
        return userSocialRepository.existsByTelegramId(telegramId);
    }

    public boolean existsByDiscordId(String discordId) {
        return userSocialRepository.existsByDiscordId(discordId);
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    public boolean existsByTradingViewName(String tradingViewName) {
        return userSocialRepository.existsByTradingViewName(tradingViewName);
    }

    public User save(User user) {
        return userRepository.save(user);
    }
}
