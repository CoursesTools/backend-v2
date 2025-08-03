package com.winworld.coursestools.validation.validator.payment.impl;

import com.winworld.coursestools.entity.Order;
import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.entity.user.UserSubscription;
import com.winworld.coursestools.enums.PaymentMethod;
import com.winworld.coursestools.exception.exceptions.PaymentProcessingException;
import com.winworld.coursestools.service.user.UserFinanceService;
import com.winworld.coursestools.validation.validator.payment.PaymentValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BalancePaymentValidator implements PaymentValidator {

    private final UserFinanceService userFinanceService;

    @Override
    public PaymentMethod getSupportedPaymentMethod() {
        return PaymentMethod.BALANCE;
    }

    @Override
    public void validate(User user, Order order, UserSubscription userSubscription) {
        if (!userFinanceService.hasEnoughBalance(order.getUser(), order.getTotalPrice())) {
            throw new PaymentProcessingException("Balance is lower than order total price");
        }
    }
}
