package com.winworld.coursestools.dto.payment.crypto;

import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;

import static com.winworld.coursestools.constants.ValidationMessages.NOT_BLANK_MESSAGE;

@Data
@NoArgsConstructor
public class CryptoRetrieveDto {

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
