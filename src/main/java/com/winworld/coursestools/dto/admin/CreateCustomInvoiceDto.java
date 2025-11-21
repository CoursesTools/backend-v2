package com.winworld.coursestools.dto.admin;

import com.winworld.coursestools.enums.Plan;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

import static com.winworld.coursestools.constants.ValidationMessages.NOT_NULL_MESSAGE;
import static com.winworld.coursestools.constants.ValidationMessages.POSITIVE_MESSAGE;

@Data
public class CreateCustomInvoiceDto {

    @NotNull(message = NOT_NULL_MESSAGE)
    @Positive(message = POSITIVE_MESSAGE)
    private Integer userId;

    @NotNull(message = NOT_NULL_MESSAGE)
    private Plan plan;

    @NotNull(message = NOT_NULL_MESSAGE)
    @Positive(message = POSITIVE_MESSAGE)
    private BigDecimal customPrice;

    private String description;

}
