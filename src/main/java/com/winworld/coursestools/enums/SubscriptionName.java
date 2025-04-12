package com.winworld.coursestools.enums;

import com.winworld.coursestools.exception.exceptions.DataValidationException;

public enum SubscriptionName {
    COURSESTOOLSPRO,
    MENTORSHIP;

    public static SubscriptionName fromString(String name) {
        try {
            return SubscriptionName.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new DataValidationException("Invalid subscription name: " + name);
        }
    }
}
