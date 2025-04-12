package com.winworld.coursestools.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@Builder
public class ReferralActivityEvent {
    private int referralId;
    private BigDecimal amount;
    private boolean isActiveChanged;
}
