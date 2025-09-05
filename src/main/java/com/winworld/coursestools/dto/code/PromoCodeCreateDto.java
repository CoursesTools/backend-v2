package com.winworld.coursestools.dto.code;

import com.winworld.coursestools.enums.DiscountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

import static com.winworld.coursestools.constants.ValidationMessages.NOT_BLANK_MESSAGE;
import static com.winworld.coursestools.constants.ValidationMessages.NOT_NULL_MESSAGE;

@Data
public class PromoCodeCreateDto {
    @NotBlank(message = NOT_BLANK_MESSAGE)
    @NotNull(message = NOT_NULL_MESSAGE)
    private String code;
    @NotNull(message = NOT_NULL_MESSAGE)
    private BigDecimal discountValue;
    @NotNull(message = NOT_NULL_MESSAGE)
    private DiscountType discountType;
    private Integer maxUses;
    private LocalDate validUntil;
}
