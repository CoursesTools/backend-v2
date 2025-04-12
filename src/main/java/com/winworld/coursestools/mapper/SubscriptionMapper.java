package com.winworld.coursestools.mapper;

import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.enums.SubscriptionEventType;
import com.winworld.coursestools.event.SubscriptionChangeStatusEvent;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import static org.mapstruct.ReportingPolicy.IGNORE;

@Mapper(componentModel = "spring", unmappedTargetPolicy = IGNORE)
public interface SubscriptionMapper {

    @Mapping(target = "tradingViewUsername", source = "user.tradingViewName")
    @Mapping(target = "email", source = "user.email")
    @Mapping(target = "expiredAt", source = "user.subscription.expiredAt")
    @Mapping(target = "eventType", source = "eventType")
    SubscriptionChangeStatusEvent toEvent(
            User user, SubscriptionEventType eventType
    );
}
