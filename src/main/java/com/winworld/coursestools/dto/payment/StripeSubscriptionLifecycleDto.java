package com.winworld.coursestools.dto.payment;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StripeSubscriptionLifecycleDto {
    private String subscriptionId;
    private Long currentPeriodEnd;
    private String status;
    private Boolean cancelAtPeriodEnd;
}
