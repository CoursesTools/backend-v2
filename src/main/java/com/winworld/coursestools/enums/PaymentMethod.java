package com.winworld.coursestools.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum PaymentMethod {
    CRYPTO, STRIPE, PAYEER, BALANCE;

    @JsonCreator
    public static PaymentMethod fromString(String value) {
        return PaymentMethod.valueOf(value.toUpperCase());
    }
}
