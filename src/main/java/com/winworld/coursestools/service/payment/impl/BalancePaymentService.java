package com.winworld.coursestools.service.payment.impl;

import com.winworld.coursestools.dto.payment.ProcessPaymentDto;
import com.winworld.coursestools.dto.payment.BalanceRetrieveDto;
import com.winworld.coursestools.dto.payment.CreatePaymentLinkDto;
import com.winworld.coursestools.entity.Order;
import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.enums.PaymentMethod;
import com.winworld.coursestools.exception.exceptions.PaymentProcessingException;
import com.winworld.coursestools.service.OrderService;
import com.winworld.coursestools.service.payment.PaymentService;
import com.winworld.coursestools.service.user.UserFinanceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class BalancePaymentService extends PaymentService<BalanceRetrieveDto> {
    private final UserFinanceService userFinanceService;
    private final OrderService orderService;

    public BalancePaymentService(OrderService orderService, UserFinanceService userFinanceService, OrderService orderService1) {
        super();
        this.userFinanceService = userFinanceService;
        this.orderService = orderService;
    }

    @Override
    public String createPaymentLink(CreatePaymentLinkDto dto) {
        // TODO Возвращать ссылку на /payments/balance а там перенаправлять
        return null;
    }

    @Override
    public PaymentMethod getPaymentMethod() {
        return PaymentMethod.BALANCE;
    }

    @Override
    @Transactional
    public ProcessPaymentDto processPayment(BalanceRetrieveDto paymentRequest) {
        Order order = orderService.getOrderById(paymentRequest.getOrderId());
        validatePayment(order.getUser(), paymentRequest.getUserId());

        userFinanceService.decreaseBalance(paymentRequest.getUserId(), order.getTotalPrice());
        log.info("User {} paid {} for order {} using balance",
                paymentRequest.getUserId(),
                order.getTotalPrice(),
                paymentRequest.getOrderId()
        );

        return ProcessPaymentDto.builder()
                .orderId(paymentRequest.getOrderId())
                .build();
    }

    private void validatePayment(User user, int userID) {
        if (!user.getId().equals(userID)) {
            throw new PaymentProcessingException("This is not your order");
        }
    }
}
