package com.winworld.coursestools.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum Currency {
    CENTS, USD;

    @JsonCreator
    public static Currency fromString(String currency) {
        return Currency.valueOf(currency.toUpperCase());
    }
}
