package com.winworld.coursestools.facade;

import com.winworld.coursestools.dto.PageDto;
import com.winworld.coursestools.dto.order.CreateOrderDto;
import com.winworld.coursestools.dto.order.ReadOrderDto;
import com.winworld.coursestools.entity.Order;
import com.winworld.coursestools.enums.PaymentMethod;
import com.winworld.coursestools.exception.exceptions.DataValidationException;
import com.winworld.coursestools.mapper.OrderMapper;
import com.winworld.coursestools.service.OrderService;
import com.winworld.coursestools.service.payment.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderFacade {
    private final OrderService orderService;
    private final List<PaymentService<?>> paymentServices;
    private final OrderMapper orderMapper;

    public PageDto<ReadOrderDto> getUserOrders(int userId, Pageable pageable) {
        try {
            return PageDto.of(orderService.getUserOrders(userId, pageable)
                    .map(orderMapper::toDto));
        } catch (InvalidDataAccessResourceUsageException e) {
            throw new DataValidationException("Invalid pagination parameters");
        }
    }

    @Transactional
    public ReadOrderDto createOrder(int userId, CreateOrderDto createDto) {
        Order order = orderService.createOrder(userId, createDto);
        ReadOrderDto dto = orderMapper.toDto(order);
        var paymentService = getNeededPaymentService(createDto.getPaymentMethod());
        dto.setPaymentLink(paymentService.createPaymentLink(orderMapper.toCreateDto(order)));
        return dto;
    }

    private PaymentService<?> getNeededPaymentService(PaymentMethod paymentMethod) {
        return paymentServices.stream()
                .filter(paymentService -> paymentService.getPaymentMethod() == paymentMethod)
                .findFirst()
                .orElseThrow(() -> new DataValidationException("Payment method not supported"));
    }
}
