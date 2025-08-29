package com.winworld.coursestools.dto.subscription;

import lombok.Data;

import java.time.LocalDate;

@Data
public class SubscriptionActivateDto {
    private String username;
    private LocalDate expiration;
}
