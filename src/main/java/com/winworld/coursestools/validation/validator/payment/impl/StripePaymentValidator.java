package com.winworld.coursestools.validation.validator.payment.impl;

import com.winworld.coursestools.entity.Order;
import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.entity.user.UserSubscription;
import com.winworld.coursestools.enums.PaymentMethod;
import com.winworld.coursestools.enums.Plan;
import com.winworld.coursestools.exception.exceptions.ConflictException;
import com.winworld.coursestools.validation.validator.payment.PaymentValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.winworld.coursestools.enums.PaymentMethod.STRIPE;
import static com.winworld.coursestools.enums.SubscriptionStatus.GRACE_PERIOD;

@Component
@RequiredArgsConstructor
public class StripePaymentValidator implements PaymentValidator {
    @Override
    public PaymentMethod getSupportedPaymentMethod() {
        return STRIPE;
    }

    @Override
    public void validate(User user, Order order, UserSubscription userSubscription) {
        if (userSubscription == null || userSubscription.getStatus().equals(GRACE_PERIOD) || userSubscription.getIsTrial()) {
            return;
        }
        Plan planName = order.getPlan().getName();
        if (planName.equals(Plan.LIFETIME) || planName.equals(Plan.TRIAL)) {
            throw new ConflictException("Stripe payment method doesn't support this plan type.");
        }
        if (userSubscription.getPaymentMethod().equals(order.getPaymentMethod()) && order.getPaymentMethod().equals(STRIPE)) {
            throw new ConflictException("You already have an active subscription with Stripe payment method.");
        }
    }
}
