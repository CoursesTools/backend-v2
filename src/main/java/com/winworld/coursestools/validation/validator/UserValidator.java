package com.winworld.coursestools.validation.validator;

import com.winworld.coursestools.dto.user.UpdateUserEmailDto;
import com.winworld.coursestools.dto.user.UpdateUserPasswordDto;
import com.winworld.coursestools.dto.user.UpdateUserDto;
import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.enums.SubscriptionName;
import com.winworld.coursestools.exception.exceptions.ConflictException;
import com.winworld.coursestools.service.CodeService;
import com.winworld.coursestools.service.SubscriptionService;
import com.winworld.coursestools.service.TokenService;
import com.winworld.coursestools.service.user.UserDataService;
import com.winworld.coursestools.service.user.UserSubscriptionService;
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
    private final UserSubscriptionService userSubscriptionService;
    private final SubscriptionService subscriptionService;

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
        if (dto.getPartnerCode() != null) {
            if (codeService.existsByCode(dto.getPartnerCode())) {
                throw new ConflictException("Code already in use");
            }
        }
        if (dto.getTermsAccepted() != null && !dto.getTermsAccepted() && user.getPartnership().getTermsAccepted()) {
            throw new ConflictException("You already accept terms");
        }
    }

    public void validateUserTelegramUpdate(String telegramId) {
        if (userDataService.existsByTelegramId(telegramId)) {
            throw new ConflictException("Telegram ID already in use");
        }
    }

    public void validateUserTradingViewUpdate(String tradingViewName, User user) {
        var subscriptionType = subscriptionService.getSubscriptionTypeByName(SubscriptionName.COURSESTOOLSPRO);
        var userSubscription = userSubscriptionService.getUserSubBySubTypeIdNotTerminated(user.getId(), subscriptionType.getId());
        if (userSubscription.isEmpty() || userSubscription.get().getIsTrial()) {
            throw new ConflictException("Cannot update TradingView name for inactive user");
        }
        if (!user.getSocial().getTradingViewName().equals(tradingViewName)
                && userDataService.existsByTradingViewName(tradingViewName)) {
            throw new ConflictException("TradingView name already in use");
        }
    }

    public void validateUserDiscordUpdate(String discordId) {
        if (userDataService.existsByDiscordId(discordId)) {
            throw new ConflictException("Discord ID already in use");
        }
    }
}
