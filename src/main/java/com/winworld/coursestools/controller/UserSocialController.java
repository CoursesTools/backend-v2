package com.winworld.coursestools.controller;

import com.winworld.coursestools.config.security.UserPrincipal;
import com.winworld.coursestools.dto.RedirectDto;
import com.winworld.coursestools.dto.user.UpdateUserDiscordDto;
import com.winworld.coursestools.dto.user.UpdateUserTelegramDto;
import com.winworld.coursestools.dto.user.UpdateUserTradingViewDto;
import com.winworld.coursestools.dto.user.UserReadDto;
import com.winworld.coursestools.service.user.UserSocialService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/users/socials")
public class UserSocialController {

    private final UserSocialService userSocialService;

    @PatchMapping("/telegram/bind")
    public UserReadDto bindUserTelegram(
            @RequestBody @Valid UpdateUserTelegramDto dto
    ) {
        return userSocialService.bindUserTelegram(dto);
    }

    @GetMapping("/me/telegram/link")
    public RedirectDto getTelegramLink(
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        return userSocialService.getTelegramLink(userPrincipal.userId());
    }

    @PatchMapping("/me/tradingview")
    public UserReadDto bindUserTelegram(
            @RequestBody @Valid UpdateUserTradingViewDto dto,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        return userSocialService.bindUserTradingView(dto, userPrincipal.userId());
    }

    @PatchMapping("/me/discord")
    public UserReadDto bindUserDiscord(
            @RequestBody @Valid UpdateUserDiscordDto dto,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        return userSocialService.bindUserDiscord(dto, userPrincipal.userId());
    }

    @DeleteMapping("/me/discord")
    public UserReadDto unbindUserDiscord(
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        return userSocialService.unbindUserDiscord(userPrincipal.userId());
    }

    @DeleteMapping("/me/telegram")
    public UserReadDto unbindUserTelegram(
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        return userSocialService.unbindUserTelegram(userPrincipal.userId());
    }
}
