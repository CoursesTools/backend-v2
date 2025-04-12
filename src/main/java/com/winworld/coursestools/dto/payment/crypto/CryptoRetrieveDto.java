package com.winworld.coursestools.dto.payment.crypto;

import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Data
@NoArgsConstructor
public class CryptoRetrieveDto {
    private static final String NOT_BLANK_MESSAGE = "The field must not be empty";

    @Setter(AccessLevel.NONE)
    @NotBlank(message = NOT_BLANK_MESSAGE)
    private String invoiceId;
    @NotBlank(message = NOT_BLANK_MESSAGE)
    private String token;
    @Setter(AccessLevel.NONE)
    @NotBlank(message = NOT_BLANK_MESSAGE)
    private String orderId;

    public void setInvoice_id(String invoiceId) {
        this.invoiceId = invoiceId;
    }

    public void setOrder_id(String orderId) {
        this.orderId = orderId;
    }
}
