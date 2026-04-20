package com.winworld.coursestools.dto.admin;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdatePartnershipCashbackDto {
    @NotNull
    private Integer userId;

    @DecimalMin("0") @DecimalMax("100")
    private BigDecimal customCashback1;

    @DecimalMin("0") @DecimalMax("100")
    private BigDecimal customCashback2;
}
