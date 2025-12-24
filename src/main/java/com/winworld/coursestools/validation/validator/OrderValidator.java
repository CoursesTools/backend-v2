package com.winworld.coursestools.validation.validator;

import com.winworld.coursestools.dto.order.CreateOrderDto;
import com.winworld.coursestools.entity.Referral;
import com.winworld.coursestools.entity.subscription.SubscriptionPlan;
import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.entity.user.UserSubscription;
import com.winworld.coursestools.exception.exceptions.ConflictException;
import com.winworld.coursestools.exception.exceptions.DataValidationException;
import com.winworld.coursestools.service.CodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

import static com.winworld.coursestools.enums.Plan.LIFETIME;
import static com.winworld.coursestools.enums.Plan.MONTH;

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

    public void validateMonthlyPlanPrice(
            SubscriptionPlan plan,
            BigDecimal subscriptionPrice,
            BigDecimal totalPrice,
            CreateOrderDto createDto,
            Referral referrer
    ) {
        if (!plan.getName().equals(MONTH)) {
            return;
        }

        BigDecimal minPrice = BigDecimal.valueOf(10);
        if (subscriptionPrice.compareTo(minPrice) < 0) {
            if (createDto.getCode() != null || (referrer != null && !referrer.isBonusUsed())) {
                throw new DataValidationException("You cannot use a promo code as your subscription price is already at the minimum ($10)");
            }
        } else {
            if (totalPrice.compareTo(minPrice) < 0) {
                throw new DataValidationException("Monthly subscription price cannot be less than $10");
            }
        }
    }
}
