package com.winworld.coursestools.dto.payment;

import com.winworld.coursestools.enums.Plan;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreatePaymentLinkDto {
    private Integer orderId;
    private String email;
    private String code;
    private Boolean isPartnershipCode;
    private BigDecimal totalPrice;
    private BigDecimal originalPrice;
    private Plan planName;
}
