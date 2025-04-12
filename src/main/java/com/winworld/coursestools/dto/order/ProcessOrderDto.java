package com.winworld.coursestools.dto.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
@AllArgsConstructor
public class ProcessOrderDto {
    private int orderId;
    private Map<String, Object> paymentProviderData;
}
