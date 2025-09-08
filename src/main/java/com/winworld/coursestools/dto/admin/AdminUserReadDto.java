package com.winworld.coursestools.dto.admin;

import com.winworld.coursestools.enums.PaymentMethod;
import com.winworld.coursestools.enums.Plan;
import com.winworld.coursestools.enums.SubscriptionName;
import com.winworld.coursestools.enums.SubscriptionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

@Data
public class AdminUserReadDto {
    @Schema(requiredMode = REQUIRED)
    private Integer id;
    @Schema(requiredMode = REQUIRED)
    private String email;
    @Schema(requiredMode = REQUIRED)
    private String tradingViewName;
    private String telegram;
    private String countryCode;
    @Schema(requiredMode = REQUIRED)
    private Integer partnershipLevel;
    @Schema(requiredMode = REQUIRED)
    private BigDecimal balance;
    private Integer referrerId;
    @Schema(requiredMode = REQUIRED)
    private List<AdminUserSubscriptionReadDto> subscriptions;
    @Schema(requiredMode = REQUIRED)
    private LocalDateTime createdAt;

    @Data
    public static class AdminUserSubscriptionReadDto {
        @Schema(requiredMode = REQUIRED)
        private Integer id;
        @Schema(requiredMode = REQUIRED)
        private Plan plan;
        @Schema(requiredMode = REQUIRED)
        private SubscriptionName subscriptionName;
        @Schema(requiredMode = REQUIRED)
        private BigDecimal price;
        @Schema(requiredMode = REQUIRED)
        private PaymentMethod paymentMethod;
        @Schema(requiredMode = REQUIRED)
        private SubscriptionStatus status;
        @Schema(requiredMode = REQUIRED)
        private Boolean isTrial;
        @Schema(requiredMode = REQUIRED)
        private Map<String, Object> paymentProviderData;
        @Schema(requiredMode = REQUIRED)
        private LocalDateTime createdAt;
        @Schema(requiredMode = REQUIRED)
        private LocalDateTime expiredAt;
    }
}
