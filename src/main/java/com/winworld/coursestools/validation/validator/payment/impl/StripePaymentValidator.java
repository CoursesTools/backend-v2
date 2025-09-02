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
        if (!order.getPlan().getName().equals(Plan.MONTH)) {
            throw new ConflictException("With Stripe payment method, you can only subscribe to the monthly plan.");
        }
        if (userSubscription.getPaymentMethod().equals(order.getPaymentMethod()) && order.getPaymentMethod().equals(STRIPE)) {
            throw new ConflictException("You already have an active subscription with Stripe payment method.");
        }
    }
}
