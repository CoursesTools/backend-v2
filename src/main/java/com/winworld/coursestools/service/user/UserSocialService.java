package com.winworld.coursestools.service.user;

import com.winworld.coursestools.dto.RedirectDto;
import com.winworld.coursestools.dto.user.UpdateUserDiscordDto;
import com.winworld.coursestools.dto.user.UpdateUserTelegramDto;
import com.winworld.coursestools.dto.user.UpdateUserTradingViewDto;
import com.winworld.coursestools.dto.user.UserReadDto;
import com.winworld.coursestools.exception.exceptions.SecurityException;
import com.winworld.coursestools.mapper.UserMapper;
import com.winworld.coursestools.service.AlertService;
import com.winworld.coursestools.service.TokenService;
import com.winworld.coursestools.service.external.OAuthDiscordService;
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

    public UserReadDto bindUserTradingView(UpdateUserTradingViewDto dto, int userId) {
        var user = userDataService.getUserById(userId);
        userValidator.validateUserTradingViewUpdate(dto.getTradingViewName(), user);
        user.getSocial().setTradingViewName(dto.getTradingViewName().toLowerCase());
        return userMapper.toDto(userDataService.save(user));
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
