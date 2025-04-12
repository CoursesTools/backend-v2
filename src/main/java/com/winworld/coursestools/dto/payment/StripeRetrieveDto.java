package com.winworld.coursestools.dto.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class StripeRetrieveDto {
    private String signature;
    private String payload;
}
