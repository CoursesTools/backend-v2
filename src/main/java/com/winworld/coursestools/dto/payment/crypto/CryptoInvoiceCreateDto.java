package com.winworld.coursestools.dto.payment.crypto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@AllArgsConstructor
@Builder
public class CryptoInvoiceCreateDto {
    @JsonProperty("shop_id")
    private String shopId;
    private float amount;
    @JsonProperty("order_id")
    private String orderId;
    private String email;
    @JsonProperty("add_fields")
    private AdditionalFields additionalFields;

    @Data
    @AllArgsConstructor
    public static class AdditionalFields {
        private Map<String, Integer> timeToPay;
    }
}
