package com.winworld.coursestools.dto.order;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.winworld.coursestools.enums.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReadOrderDto {
    @Schema(requiredMode = REQUIRED)
    private int id;
    @Schema(requiredMode = REQUIRED)
    private int userId;
    @Schema(requiredMode = REQUIRED)
    private PaymentMethod paymentMethod;
    @Schema(requiredMode = REQUIRED)
    private String paymentLink;
    private String code;
    @Schema(requiredMode = REQUIRED)
    private String plan;
    @Schema(requiredMode = REQUIRED)
    private Integer totalPrice;
    @Schema(requiredMode = REQUIRED)
    private Integer originalPrice;
    @Schema(requiredMode = REQUIRED)
    private LocalDateTime createdAt;
}
