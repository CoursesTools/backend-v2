package com.winworld.coursestools.service.user;

import com.winworld.coursestools.dto.RedirectDto;
import com.winworld.coursestools.dto.external.ActivateTradingViewAccessDto;
import com.winworld.coursestools.dto.external.ChangeTradingViewNameDto;
import com.winworld.coursestools.dto.user.UpdateUserDiscordDto;
import com.winworld.coursestools.dto.user.UpdateUserTelegramDto;
import com.winworld.coursestools.dto.user.UpdateUserTradingViewDto;
import com.winworld.coursestools.dto.user.UserReadDto;
import com.winworld.coursestools.entity.subscription.SubscriptionType;
import com.winworld.coursestools.entity.user.UserSubscription;
import com.winworld.coursestools.enums.Plan;
import com.winworld.coursestools.enums.SubscriptionName;
import com.winworld.coursestools.exception.exceptions.ConflictException;
import com.winworld.coursestools.exception.exceptions.EntityNotFoundException;
import com.winworld.coursestools.exception.exceptions.SecurityException;
import com.winworld.coursestools.mapper.UserMapper;
import com.winworld.coursestools.repository.TrialActivationRepository;
import com.winworld.coursestools.service.AlertService;
import com.winworld.coursestools.service.SubscriptionService;
import com.winworld.coursestools.service.TokenService;
import com.winworld.coursestools.service.external.ActivatingSubscriptionService;
import com.winworld.coursestools.service.external.OAuthDiscordService;
import com.winworld.coursestools.service.external.TradingViewRetryService;
import com.winworld.coursestools.validation.validator.UserValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserSocialService {

    private final TokenService tokenService;
    private final UserDataService userDataService;
    private final UserMapper userMapper;
    private final UserValidator userValidator;
    private final OAuthDiscordService oAuthDiscordService;
    private final AlertService alertService;
    private final ActivatingSubscriptionService activatingSubscriptionService;
    private final TradingViewRetryService tradingViewRetryService;
    private final SubscriptionService subscriptionService;
    private final UserSubscriptionService userSubscriptionService;
    private final TrialActivationRepository trialActivationRepository;

    @Value("${urls.telegram-bot}")
    private String urlTelegramBot;

    public UserReadDto bindUserTelegram(UpdateUserTelegramDto dto) {
        var userId = tokenService.getAndDeleteTelegramToken(dto.getToken());
        if (userId == null) {
            throw new SecurityException("Invalid or expired token");
        }
        userValidator.validateUserTelegramUpdate(dto.getTelegramId());
        var user = userDataService.getUserById(userId);
        user.getSocial().setTelegramId(dto.getTelegramId());
        return userMapper.toDto(userDataService.save(user));
    }

    public RedirectDto getTelegramLink(int userId) {
        var token = tokenService.saveAndGetTelegramToken(userId);
        return new RedirectDto(urlTelegramBot + "?start=" + token);
    }

    @Transactional
    public UserReadDto bindUserTradingView(UpdateUserTradingViewDto dto, int userId) {
        var user = userDataService.getUserById(userId);
        userValidator.validateUserTradingViewUpdate(dto.getTradingViewName(), user);
        // Capture the current name BEFORE mutating so the TV bot rename call
        // gets the correct old→new pair. Previously both old and new were read
        // after the setter, which sent old==new to the bot (a no-op/error).
        String oldTradingViewName = user.getSocial().getTradingViewName();
        String newTradingViewName = dto.getTradingViewName().toLowerCase();
        user.getSocial().setTradingViewName(newTradingViewName);
        // Keep any in-flight ACTIVATE retry pointed at the user's new handle.
        // Same tx: if the rename fails below (non-retryable), this patch rolls back too.
        tradingViewRetryService.onUserTradingViewNameChanged(user.getId(), newTradingViewName);

        SubscriptionType subscriptionType = subscriptionService.getSubscriptionTypeByName(SubscriptionName.COURSESTOOLS);
        var userSubscriptionOptional = userSubscriptionService.getCurrentUserSubBySubTypeId(
                user.getId(), subscriptionType.getId()
        );
        if (userSubscriptionOptional.isPresent()) {
            var userSubscription = userSubscriptionOptional.get();
            if (userSubscription.getIsTrial()
                    && trialActivationRepository.existsByTradingviewUsername(newTradingViewName)) {
                throw new ConflictException("TradingView username already used for trial");
            }
            // Skip the bot rename if nothing actually changed (case-only normalization).
            if (!newTradingViewName.equalsIgnoreCase(oldTradingViewName)) {
                var changeNameDto = new ChangeTradingViewNameDto(
                        oldTradingViewName,
                        newTradingViewName,
                        userSubscription.getPlan().getTier(),
                        userSubscription.getExpiredAt(),
                        userSubscription.getPlan().getName() == Plan.LIFETIME
                );
                activatingSubscriptionService.changeTradingViewUsername(user.getId(), changeNameDto);
            }
        }
        var savedUser = userDataService.save(user);
        return userMapper.toDto(savedUser);
    }

    public UserReadDto bindUserDiscord(UpdateUserDiscordDto dto, int userId) {
        var user = userDataService.getUserById(userId);
        user.getSocial().setDiscordId(
                oAuthDiscordService.getUserInfo(dto.getDiscordAuthCode()).getId()
        );
        return userMapper.toDto(userDataService.save(user));
    }

    public UserReadDto unbindUserDiscord(int userId) {
        var user = userDataService.getUserById(userId);
        user.getSocial().setDiscordId(null);
        return userMapper.toDto(userDataService.save(user));
    }

    @Transactional
    public UserReadDto unbindUserTelegram(int userId) {
        var user = userDataService.getUserById(userId);
        alertService.unSubscribeOnAllAlerts(userId);
        user.getSocial().setTelegramId(null);
        return userMapper.toDto(userDataService.save(user));
    }
}
