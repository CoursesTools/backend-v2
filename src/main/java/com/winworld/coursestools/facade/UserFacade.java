package com.winworld.coursestools.facade;

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
import com.winworld.coursestools.entity.subscription.SubscriptionType;
import com.winworld.coursestools.enums.SubscriptionName;
import com.winworld.coursestools.service.PartnershipService;
import com.winworld.coursestools.service.ReferralService;
import com.winworld.coursestools.service.SubscriptionService;
import com.winworld.coursestools.service.user.UserService;
import com.winworld.coursestools.service.user.UserSubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserFacade {
    private final UserSubscriptionService userSubscriptionService;
    private final UserService userService;
    private final PartnershipService partnershipService;
    private final SubscriptionService subscriptionService;
    private final ReferralService referralService;

    public List<UserSubscriptionReadDto> getUsersSubscriptions(
            UsersSubscriptionsFilterDto filterDto, int userId
    ) {
        return userSubscriptionService.getUserSubscriptionsByFilter(filterDto, userId);
    }

    public UserReadDto getUser(int userId) {
        return userService.getUser(userId);
    }

    public UserPartnershipReadDto getUserPartnership(int userId) {
        return partnershipService.getUserPartnership(userId);
    }

    public UserReadDto updateUser(int userId, UpdateUserPasswordDto userUpdateDto) {
        return userService.updateUser(userId, userUpdateDto);
    }

    public UserReadDto updateUser(int userId, UpdateUserDto userUpdateDto) {
        return userService.updateUser(userId, userUpdateDto);
    }

    public UserReadDto updateUser(int userId, UpdateUserEmailDto userUpdateDto) {
        return userService.updateUser(userId, userUpdateDto);
    }

    public SubscriptionUsedDto hasEverUsedSubscription(
            int userId, SubscriptionName subscriptionName
    ) {
        SubscriptionType subscriptionType = subscriptionService.getSubscriptionTypeByName(subscriptionName);
        return SubscriptionUsedDto.builder()
                .used(userSubscriptionService.hasEverHadSubscriptionOfType(
                        userId,
                        subscriptionType.getId()
                ))
                .build();
    }

    public PageDto<UserPartnerReadDto> getUserPartners(int userId, Pageable pageable) {
        return referralService.findUserPartnersByReferrerId(userId, pageable);
    }
}
