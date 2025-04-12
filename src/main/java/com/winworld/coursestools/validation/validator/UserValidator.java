package com.winworld.coursestools.validation.validator;

import com.winworld.coursestools.dto.user.UpdateUserEmailDto;
import com.winworld.coursestools.dto.user.UpdateUserPasswordDto;
import com.winworld.coursestools.dto.user.UpdateUserDto;
import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.exception.exceptions.ConflictException;
import com.winworld.coursestools.service.CodeService;
import com.winworld.coursestools.service.TokenService;
import com.winworld.coursestools.service.user.UserDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserValidator {
    private final UserDataService userDataService;
    private final CodeService codeService;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public void validateUserEmailUpdate(UpdateUserEmailDto dto, User user) {
        if (userDataService.existsByEmail(dto.getEmail())) {
            throw new ConflictException("Email already in use");
        }
        if (dto.getEmailCode() != null && !tokenService.getEmailToken(user.getId()).equals(dto.getEmailCode())) {
            throw new ConflictException("Email code is not valid or expired");
        }
    }

    public void validateUserPasswordUpdate(UpdateUserPasswordDto dto, User user) {
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new ConflictException("Incorrect password");
        }
        if (!dto.getNewPassword().equals(dto.getNewPasswordConfirm())) {
            throw new ConflictException("New password and confirmation do not match");
        }
    }

    public void validateUserUpdate(UpdateUserDto dto, User user) {
        if (dto.getTradingViewName() != null) {
            if (user.getReferred() == null || !user.getReferred().isActive()) {
                throw new ConflictException("Cannot update TradingView name for inactive user");
            }
            if (!user.getTradingViewName().equals(dto.getTradingViewName())
                    && userDataService.existsByTradingViewName(dto.getTradingViewName())) {
                throw new ConflictException("TradingView name already in use");
            }
        }
        if (dto.getTelegramId() != null && user.getTelegramId() != null) {
            dto.getTelegramId().ifPresent(telegramId -> {
                if (!user.getTelegramId().equals(telegramId) && userDataService.existsByTelegramId(telegramId)) {
                    throw new ConflictException("Telegram ID already in use");
                }
            });
        }
        if (dto.getDiscordId() != null && user.getProfile().getDiscordId() != null) {
            dto.getDiscordId().ifPresent(discordId -> {
                if (!user.getProfile().getDiscordId().equals(discordId)
                        && userDataService.existsByDiscordId(discordId)) {
                    throw new ConflictException("Discord ID already in use");
                }
            });
        }
        if (dto.getPartnerCode() != null) {
            if (codeService.existsByCode(dto.getPartnerCode())) {
                throw new ConflictException("Code already in use");
            }
        }
        if (dto.getTermsAccepted() != null && !dto.getTermsAccepted() && user.getPartnership().getTermsAccepted()) {
            throw new ConflictException("You already accept terms");
        }
    }
}
