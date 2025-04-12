package com.winworld.coursestools.mapper;

import com.winworld.coursestools.dto.user.UserSubscriptionReadDto;
import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.entity.user.UserSubscription;
import com.winworld.coursestools.event.UserCreateEvent;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import static org.mapstruct.ReportingPolicy.WARN;

@Mapper(componentModel = "spring", unmappedTargetPolicy = WARN)
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

    @Mapping(target = "expiredAt", source = "expiredAt")
    @Mapping(target = "isTrial", source = "trial")
    @Mapping(target = "plan", source = "plan")
    @Mapping(target = "paymentMethod", source = "paymentMethod")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "subscriptionPrice", source = "subscriptionPrice")
    UserSubscriptionReadDto toDto(UserSubscription userSubscription);
}
