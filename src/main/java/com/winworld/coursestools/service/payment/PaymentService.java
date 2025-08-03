package com.winworld.coursestools.service.payment;

import com.winworld.coursestools.dto.payment.ProcessPaymentDto;
import com.winworld.coursestools.dto.payment.CreatePaymentLinkDto;
import com.winworld.coursestools.enums.PaymentMethod;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;

@RequiredArgsConstructor
public abstract class PaymentService<T> {
    public abstract String createPaymentLink(CreatePaymentLinkDto dto);

    public abstract PaymentMethod getPaymentMethod();

    public abstract ProcessPaymentDto processPayment(T paymentRequest);

    protected Float getPriceInUsd(BigDecimal price) {
        return price
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                .floatValue();
    }
}
