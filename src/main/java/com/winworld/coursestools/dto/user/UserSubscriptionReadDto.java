package com.winworld.coursestools.dto.user;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.winworld.coursestools.enums.PaymentMethod;
import com.winworld.coursestools.enums.Plan;
import com.winworld.coursestools.enums.SubscriptionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserSubscriptionReadDto {
    private Plan plan;
    @JsonProperty("payment_method")
    private PaymentMethod paymentMethod;
    @JsonProperty("subscription_price")
    private BigDecimal subscriptionPrice;
    @JsonProperty("is_trial")
    private boolean isTrial;
    @JsonProperty("expired_at")
    private LocalDateTime expiredAt;
    private SubscriptionStatus status;
}
