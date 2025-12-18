package com.winworld.coursestools.validation.validator.payment.impl;

import com.winworld.coursestools.entity.Order;
import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.entity.user.UserSubscription;
import com.winworld.coursestools.enums.PaymentMethod;
import com.winworld.coursestools.exception.exceptions.PaymentProcessingException;
import com.winworld.coursestools.validation.validator.payment.PaymentValidator;
import org.springframework.stereotype.Component;

@Component
public class PayeerPaymentValidator implements PaymentValidator {

    @Override
    public PaymentMethod getSupportedPaymentMethod() {
        return PaymentMethod.PAYEER;
    }

    @Override
    public void validate(User user, Order order, UserSubscription userSubscription) {
        throw new PaymentProcessingException("Payeer payment method is no longer supported. Please use CRYPTO or STRIPE instead.");
    }
}