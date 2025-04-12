package com.winworld.coursestools.service.payment;

import com.winworld.coursestools.entity.Order;
import com.winworld.coursestools.enums.PaymentMethod;
import com.winworld.coursestools.exception.exceptions.ConflictException;
import com.winworld.coursestools.service.OrderService;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;

@RequiredArgsConstructor
public abstract class PaymentService<T> {
    protected final OrderService orderService;

    public abstract String createPaymentLink(int orderId);

    public abstract PaymentMethod getPaymentMethod();

    protected void verifyPaymentMethodCompatibility(int orderId) {
        Order order = orderService.getOrderById(orderId);
        if (!order.getPaymentMethod().equals(getPaymentMethod())) {
            throw new ConflictException("Invalid payment method for order ID: " + orderId);
        }
    };

    public abstract void processPayment(T paymentRequest);

    protected Float getPriceInUsd(BigDecimal price) {
        return price
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                .floatValue();
    }
}
