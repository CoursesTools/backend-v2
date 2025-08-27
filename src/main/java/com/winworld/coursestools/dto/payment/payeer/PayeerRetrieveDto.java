package com.winworld.coursestools.dto.payment.payeer;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PayeerRetrieveDto {
    @JsonProperty("m_shop")
    private String merchantId;
    @JsonProperty("m_operation_ps")
    private String operationPs;
    @JsonProperty("m_operation_date")
    private String operationDate;
    @JsonProperty("m_operation_pay_date")
    private String operationPayDate;
    @JsonProperty("m_operation_id")
    private String operationId;
    @JsonProperty("m_orderid")
    private String orderId;
    @JsonProperty("m_amount")
    private String amount;
    @JsonProperty("m_curr")
    private String currency;
    @JsonProperty("m_desc")
    private String description;
    @JsonProperty("m_status")
    private String status;
    @JsonProperty("m_sign")
    private String signature;
}
