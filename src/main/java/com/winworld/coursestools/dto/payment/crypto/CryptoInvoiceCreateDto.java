package com.winworld.coursestools.dto.payment.crypto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CryptoInvoiceCreateDto {
    @JsonProperty("shop_id")
    private String shopId;
    private float amount;
    /**
     * Required by CryptoCloud V2 API. Always "USD" for our integration.
     */
    private String currency;
    @JsonProperty("order_id")
    private String orderId;
    private String email;
    @JsonProperty("add_fields")
    private AdditionalFields additionalFields;

    @Data
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AdditionalFields {
        @JsonProperty("time_to_pay")
        private Map<String, Integer> timeToPay;
    }
}
