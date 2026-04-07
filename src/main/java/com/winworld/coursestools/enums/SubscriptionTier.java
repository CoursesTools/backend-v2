package com.winworld.coursestools.enums;

import com.winworld.coursestools.exception.exceptions.DataValidationException;

public enum SubscriptionTier {
    ESSENTIALS, PRO;

    public static SubscriptionTier fromString(String tier) {
        try {
            return SubscriptionTier.valueOf(tier.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new DataValidationException("Invalid subscription tier: " + tier);
        }
    }
}
