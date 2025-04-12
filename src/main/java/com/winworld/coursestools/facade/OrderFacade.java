package com.winworld.coursestools.facade;

import com.winworld.coursestools.dto.order.CreateOrderDto;
import com.winworld.coursestools.dto.order.ReadOrderDto;
import com.winworld.coursestools.enums.PaymentMethod;
import com.winworld.coursestools.exception.DataValidationException;
import com.winworld.coursestools.service.OrderService;
import com.winworld.coursestools.service.payment.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderFacade {
    private final OrderService orderService;
    private final List<PaymentService<?>> paymentServices;

    @Transactional
    public ReadOrderDto createOrder(int userId, CreateOrderDto createDto) {
        ReadOrderDto orderDto = orderService.createOrder(userId, createDto);
        var paymentService = getNeededPaymentService(createDto.getPaymentMethod());
        orderDto.setPaymentLink(paymentService.createPaymentLink(orderDto.getId()));
        return orderDto;
    }

    private PaymentService<?> getNeededPaymentService(PaymentMethod paymentMethod) {
        return paymentServices.stream()
                .filter(paymentService -> paymentService.getPaymentMethod() == paymentMethod)
                .findFirst()
                .orElseThrow(() -> new DataValidationException("Payment method not supported"));
    }
}
