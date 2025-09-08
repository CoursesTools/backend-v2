package com.winworld.coursestools.mapper;

import com.winworld.coursestools.dto.admin.AdminUserReadDto;
import com.winworld.coursestools.dto.user.UpdateUserDto;
import com.winworld.coursestools.dto.user.UserReadDto;
import com.winworld.coursestools.dto.user.UserSubscriptionReadDto;
import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.entity.user.UserSubscription;
import com.winworld.coursestools.event.UserCreateEvent;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import static org.mapstruct.NullValuePropertyMappingStrategy.IGNORE;
import static org.mapstruct.ReportingPolicy.WARN;

@Mapper(componentModel = "spring", unmappedTargetPolicy = WARN, uses = {JsonNullableMapper.class})
public interface UserMapper {
    @Mapping(target = "generatedPassword", ignore = true)
    @Mapping(target = "forwardedFor", source = "forwardedFor")
    @Mapping(target = "id", source = "user.id")
    UserCreateEvent toEvent(User user, String forwardedFor);

    @Mapping(target = "forwardedFor", source = "forwardedFor")
    @Mapping(target = "id", source = "user.id")
    @Mapping(target = "generatedPassword", source = "generatedPassword")
    @Mapping(target = "email", source = "user.email")
    UserCreateEvent toEvent(User user, String forwardedFor, String generatedPassword);

    @Mapping(target = "isTrial", source = "isTrial")
    @Mapping(target = "plan", source = "plan.name")
    @Mapping(target = "planId", source = "plan.id")
    UserSubscriptionReadDto toDto(UserSubscription userSubscription);

    @Mapping(target = "isReferralBonusUsed", source = "referred.bonusUsed")
    @Mapping(target = "discordId", source = "social.discordId")
    @Mapping(target = "telegramId", source = "social.telegramId")
    @Mapping(target = "tradingViewName", source = "social.tradingViewName")
    @Mapping(target = "balance", source = "finance.balance")
    @Mapping(target = "isActive", source = "referred.active", defaultValue = "false")
    UserReadDto toDto(User user);

    @Mapping(target = "userTransactions", ignore = true)
    @Mapping(target = "userAlerts", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "subscriptions", ignore = true)
    @Mapping(target = "social", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "referred", ignore = true)
    @Mapping(target = "referrals", ignore = true)
    @Mapping(target = "profile", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "orders", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "finance", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = IGNORE)
    @Mapping(target = "partnerCode.code", source = "dto.partnerCode")
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "partnership.termsAccepted", source = "dto.termsAccepted")
    void updateUserFromDto(UpdateUserDto dto, @MappingTarget User user);

    @Mapping(target = "telegram", source = "social.telegramId")
    @Mapping(target = "referrerId", source = "referred.referrer.id")
    @Mapping(target = "partnershipLevel", source = "partnership.level")
    @Mapping(target = "countryCode", source = "profile.countryCode")
    @Mapping(target = "balance", source = "finance.balance")
    @Mapping(target = "tradingViewName", source = "social.tradingViewName")
    AdminUserReadDto toAdminDto(User user);

    @Mapping(target = "plan", source = "plan.name")
    @Mapping(target = "subscriptionName", source = "plan.subscriptionType.name")
    AdminUserReadDto.AdminUserSubscriptionReadDto toAdminSubscriptionDto(UserSubscription userSubscription);

    default User map(int userId) {
        return new User(userId);
    }
}
