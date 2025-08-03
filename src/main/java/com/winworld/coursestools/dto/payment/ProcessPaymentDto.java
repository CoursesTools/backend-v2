package com.winworld.coursestools.dto.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProcessPaymentDto {
    private int orderId;
    private Map<String, Object> paymentProviderData;
}
