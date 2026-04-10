package com.winworld.coursestools.dto.payment.crypto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

import static com.winworld.coursestools.constants.ValidationMessages.NOT_BLANK_MESSAGE;

/**
 * CryptoCloud V2 postback payload. The endpoint accepts both
 * {@code application/x-www-form-urlencoded} (legacy default) and
 * {@code application/json}. Snake-case setters {@link #setInvoice_id} /
 * {@link #setOrder_id} are kept for Spring form binding; Jackson uses the
 * {@link JsonProperty} mappings on the fields for JSON.
 */
@Data
@NoArgsConstructor
public class CryptoRetrieveDto {

    @NotBlank(message = NOT_BLANK_MESSAGE)
    @JsonProperty("invoice_id")
    @JsonAlias({"invoiceId"})
    private String invoiceId;

    @NotBlank(message = NOT_BLANK_MESSAGE)
    private String token;

    @NotBlank(message = NOT_BLANK_MESSAGE)
    @JsonProperty("order_id")
    @JsonAlias({"orderId"})
    private String orderId;

    /**
     * CryptoCloud V2 postback status. Documented value for successful payment is "success".
     * Optional so legacy integrations / form-urlencoded postbacks without the field still parse,
     * but {@link com.winworld.coursestools.service.payment.impl.CryptoPaymentService} only
     * grants access when this equals {@code STATUS_SUCCESS}.
     */
    private String status;

    public void setInvoice_id(String invoiceId) {
        this.invoiceId = invoiceId;
    }

    public void setOrder_id(String orderId) {
        this.orderId = orderId;
    }
}
