package com.winworld.coursestools.dto.user;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.winworld.coursestools.enums.PaymentMethod;
import com.winworld.coursestools.enums.Plan;
import com.winworld.coursestools.enums.SubscriptionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserSubscriptionReadDto {
    @Schema(requiredMode = REQUIRED)
    private int id;
    @Schema(requiredMode = REQUIRED)
    private Plan plan;
    @Schema(requiredMode = REQUIRED)
    private int planId;
    @Schema(requiredMode = REQUIRED)
    private PaymentMethod paymentMethod;
    @Schema(requiredMode = REQUIRED)
    private BigDecimal price;
    @Schema(requiredMode = REQUIRED)
    private Boolean isTrial;
    @Schema(requiredMode = REQUIRED)
    private LocalDateTime expiredAt;
    @Schema(requiredMode = REQUIRED)
    private LocalDateTime createdAt;
    @Schema(requiredMode = REQUIRED)
    private SubscriptionStatus status;
}
