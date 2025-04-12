package com.winworld.coursestools.dto.order;

import com.winworld.coursestools.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import static com.winworld.coursestools.constants.ValidationMessages.NOT_NULL_MESSAGE;
import static com.winworld.coursestools.constants.ValidationMessages.POSITIVE_MESSAGE;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateOrderDto {

    @NotNull(message = NOT_NULL_MESSAGE)
    @Positive(message = POSITIVE_MESSAGE)
    private int planId;

    private String code;

    @NotNull(message = NOT_NULL_MESSAGE)
    private PaymentMethod paymentMethod;

}
