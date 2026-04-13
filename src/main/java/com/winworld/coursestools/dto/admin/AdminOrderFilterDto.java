package com.winworld.coursestools.dto.admin;

import com.winworld.coursestools.enums.OrderStatus;
import com.winworld.coursestools.enums.OrderType;
import com.winworld.coursestools.enums.PaymentMethod;
import com.winworld.coursestools.enums.SubscriptionTier;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Data
public class AdminOrderFilterDto {
    private Integer orderId;
    private Integer userId;
    private String email;
    private String tradingViewName;
    private OrderStatus status;
    private PaymentMethod paymentMethod;
    private SubscriptionTier tier;
    private OrderType orderType;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime createdFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime createdTo;
}
