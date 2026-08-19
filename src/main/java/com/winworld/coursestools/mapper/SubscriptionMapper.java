package com.winworld.coursestools.mapper;

import com.winworld.coursestools.dto.subscription.SubscriptionPlanReadDto;
import com.winworld.coursestools.dto.subscription.SubscriptionReadDto;
import com.winworld.coursestools.entity.subscription.SubscriptionPlan;
import com.winworld.coursestools.entity.subscription.SubscriptionType;
import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.entity.user.UserSubscription;
import com.winworld.coursestools.enums.SubscriptionEventType;
import com.winworld.coursestools.enums.TradingViewExpirationPolicy;
import com.winworld.coursestools.event.SubscriptionChangeStatusEvent;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDateTime;

import static org.mapstruct.ReportingPolicy.IGNORE;

@Mapper(componentModel = "spring", unmappedTargetPolicy = IGNORE)
public interface SubscriptionMapper {

    @Mapping(target = "tradingViewUsername", source = "user.social.tradingViewName")
    @Mapping(target = "email", source = "user.email")
    @Mapping(target = "eventType", source = "eventType")
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "userSubscriptionId", source = "userSubscription.id")
    @Mapping(target = "tradingViewExpirationPolicy", source = "tradingViewExpirationPolicy")
    @Mapping(target = "expiration", source = "userSubscription.expiredAt")
    @Mapping(target = "tier", source = "userSubscription.plan.tier")
    @Mapping(target = "lifetime", expression = "java(userSubscription.getPlan().getName() == com.winworld.coursestools.enums.Plan.LIFETIME)")
    @Mapping(target = "activationCommandId", ignore = true)
    SubscriptionChangeStatusEvent toEvent(
            User user,
            SubscriptionEventType eventType,
            UserSubscription userSubscription,
            TradingViewExpirationPolicy tradingViewExpirationPolicy
    );

    SubscriptionReadDto toDto(SubscriptionType subscriptionType);

    SubscriptionPlanReadDto toDto(SubscriptionPlan subscriptionPlan);
}
