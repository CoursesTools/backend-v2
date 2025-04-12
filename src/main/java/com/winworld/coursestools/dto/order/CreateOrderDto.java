package com.winworld.coursestools.dto.order;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.winworld.coursestools.enums.PaymentMethod;
import com.winworld.coursestools.enums.Plan;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateOrderDto {
    private static final String NOT_NULL_MESSAGE = "The field must not be null";

    @NotNull(message = NOT_NULL_MESSAGE)
    private Plan plan;

    @JsonProperty("promo_code")
    private String promoCode;

    @NotNull(message = NOT_NULL_MESSAGE)
    @JsonProperty("payment_method")
    private PaymentMethod paymentMethod;

}
