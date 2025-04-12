package com.winworld.coursestools.controller;

import com.winworld.coursestools.config.security.UserPrincipal;
import com.winworld.coursestools.dto.PageDto;
import com.winworld.coursestools.dto.partnership.UserPartnerReadDto;
import com.winworld.coursestools.dto.user.SubscriptionUsedDto;
import com.winworld.coursestools.dto.partnership.UserPartnershipReadDto;
import com.winworld.coursestools.dto.user.UpdateUserEmailDto;
import com.winworld.coursestools.dto.user.UpdateUserPasswordDto;
import com.winworld.coursestools.dto.user.UpdateUserDto;
import com.winworld.coursestools.dto.user.UserReadDto;
import com.winworld.coursestools.dto.user.UserSubscriptionReadDto;
import com.winworld.coursestools.dto.user.UsersSubscriptionsFilterDto;
import com.winworld.coursestools.enums.SubscriptionName;
import com.winworld.coursestools.facade.UserFacade;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final UserFacade userFacade;

    @GetMapping("/me/subscriptions")
    public List<UserSubscriptionReadDto> getUsersSubscriptions(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @ParameterObject UsersSubscriptionsFilterDto filterDto
    ) {
        return userFacade.getUsersSubscriptions(filterDto, userPrincipal.userId());
    }

    @GetMapping("/me")
    public UserReadDto getUser(
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        return userFacade.getUser(userPrincipal.userId());
    }

    @GetMapping("/me/partnership")
    public UserPartnershipReadDto getUserPartnership(
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        return userFacade.getUserPartnership(userPrincipal.userId());
    }

    @GetMapping("/me/partners")
    public PageDto<UserPartnerReadDto> getUserPartners(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @ParameterObject Pageable pageable
    ) {
        return userFacade.getUserPartners(userPrincipal.userId(), pageable);
    }

    @PatchMapping("/me/password")
    public UserReadDto updateUser(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody @Valid UpdateUserPasswordDto dto
    ) {
        return userFacade.updateUser(userPrincipal.userId(), dto);
    }

    @PatchMapping("/me/email")
    public UserReadDto updateUser(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody @Valid UpdateUserEmailDto dto
    ) {
        return userFacade.updateUser(userPrincipal.userId(), dto);
    }

    @PatchMapping("/me")
    public UserReadDto updateUser(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody @Valid UpdateUserDto dto
    ) {
        return userFacade.updateUser(userPrincipal.userId(), dto);
    }

    @GetMapping("/me/subscriptions/has-used")
    public SubscriptionUsedDto hasEverUsedSubscription(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam("subscriptionName") String subscriptionName
    ) {
        return userFacade.hasEverUsedSubscription(
                userPrincipal.userId(),
                SubscriptionName.fromString(subscriptionName)
        );
    }
}
