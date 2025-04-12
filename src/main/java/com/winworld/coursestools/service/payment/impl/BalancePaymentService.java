package com.winworld.coursestools.service.payment.impl;

import com.winworld.coursestools.dto.order.ProcessOrderDto;
import com.winworld.coursestools.dto.payment.BalanceRetrieveDto;
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

    public BalancePaymentService(OrderService orderService, UserFinanceService userFinanceService) {
        super(orderService);
        this.userFinanceService = userFinanceService;
    }

    @Override
    public String createPaymentLink(int orderId) {
        Order order = orderService.getOrderById(orderId);
        if (order.getUser().getFinance().getBalance().compareTo(order.getTotalPrice()) < 0) {
            throw new PaymentProcessingException("Balance is lower than order total price");
        }
        return null;
    }

    @Override
    public PaymentMethod getPaymentMethod() {
        return PaymentMethod.BALANCE;
    }

    @Override
    @Transactional
    public void processPayment(BalanceRetrieveDto paymentRequest) {
        Order order = orderService.getOrderById(paymentRequest.getOrderId());
        validatePayment(order.getUser(), paymentRequest.getUserId());
        verifyPaymentMethodCompatibility(paymentRequest.getOrderId());

        userFinanceService.decreaseBalance(paymentRequest.getUserId(), order.getTotalPrice());
        log.info("User {} paid {} for order {} using balance",
                paymentRequest.getUserId(),
                order.getTotalPrice(),
                paymentRequest.getOrderId()
        );

        ProcessOrderDto dto = ProcessOrderDto.builder()
                .orderId(paymentRequest.getOrderId())
                .build();
        orderService.processSuccessfulPayment(dto);
    }

    private void validatePayment(User user, int userID) {
        if (!user.getId().equals(userID)) {
            throw new PaymentProcessingException("This is not your order");
        }
    }
}
