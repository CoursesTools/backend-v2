package com.winworld.coursestools.service.user;

import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.exception.exceptions.EntityNotFoundException;
import com.winworld.coursestools.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserDataService {
    private static final String EMAIL_NOT_FOUND = "User with email %s not found";
    private static final String ID_NOT_FOUND = "User with id %d not found";
    private static final String PARTNER_CODE_NOT_FOUND = "User with partner code %s not found";

    private final UserRepository userRepository;

    public User getUserByEmail(String email) {
        return userRepository.findUserByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException(
                        String.format(EMAIL_NOT_FOUND, email)
                ));
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
        return userRepository.existsByTelegramId(telegramId);
    }

    public boolean existsByDiscordId(String discordId) {
        return userRepository.existsByProfileDiscordId(discordId);
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    public boolean existsByTradingViewName(String tradingViewName) {
        return userRepository.existsByTradingViewName(tradingViewName);
    }

    public User save(User user) {
        return userRepository.save(user);
    }
}
