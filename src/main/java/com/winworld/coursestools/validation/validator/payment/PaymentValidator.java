package com.winworld.coursestools.validation.validator.payment;

import com.winworld.coursestools.entity.Order;
import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.entity.user.UserSubscription;
import com.winworld.coursestools.enums.PaymentMethod;

public interface PaymentValidator {
    PaymentMethod getSupportedPaymentMethod();
    void validate(User user, Order order, UserSubscription userSubscription);
}
