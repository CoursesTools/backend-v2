package com.winworld.coursestools.dto.payment.payeer;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class PayeerCreatePaymentDto {
    @JsonProperty("m_shop")
    private String merchantId;

    @JsonProperty("m_orderid")
    private String orderId;

    @JsonProperty("m_amount")
    private String amount;

    @JsonProperty("m_curr")
    private String currency;

    @JsonProperty("m_desc")
    private String description;

    @JsonProperty("m_sign")
    private String signature;
}
