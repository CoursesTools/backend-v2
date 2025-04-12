package com.winworld.coursestools.validation.validator;

import com.winworld.coursestools.dto.order.CreateOrderDto;
import com.winworld.coursestools.entity.subscription.SubscriptionPlan;
import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.entity.user.UserSubscription;
import com.winworld.coursestools.exception.exceptions.ConflictException;
import com.winworld.coursestools.service.CodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.winworld.coursestools.enums.Plan.LIFETIME;

@Component
@RequiredArgsConstructor
public class OrderValidator {
    private final CodeService codeService;

    public void validateCreateOrder(User user, CreateOrderDto dto, UserSubscription userSubscription) {
        if (userSubscription != null) {
            SubscriptionPlan userSubscriptionPlan = userSubscription.getPlan();
            if (userSubscriptionPlan.getName().equals(LIFETIME)) {
                throw new ConflictException("You already have a lifetime plan");

            }
        }

        if (dto.getCode() != null) {
            codeService.checkCode(user.getId(), dto.getCode());
        }
    }
}
