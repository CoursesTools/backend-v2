package com.winworld.coursestools.dto.code;

import com.winworld.coursestools.enums.DiscountType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

@Data
public class CodeReadDto {
    @Schema(requiredMode = REQUIRED)
    private int id;
    @Schema(requiredMode = REQUIRED)
    private String code;
    @Schema(requiredMode = REQUIRED)
    private Boolean isPartnership;
    @Schema(requiredMode = REQUIRED)
    private DiscountType discountType;
    @Schema(requiredMode = REQUIRED)
    private BigDecimal discountValue;
    @Schema(requiredMode = NOT_REQUIRED)
    private LocalDate validUntil;
    @Schema(requiredMode = NOT_REQUIRED)
    private Integer maxUses;

}
