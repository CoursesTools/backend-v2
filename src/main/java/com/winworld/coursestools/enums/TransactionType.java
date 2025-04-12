package com.winworld.coursestools.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.winworld.coursestools.exception.DataValidationException;

public enum TransactionType {
    WITHDRAWAL,
    PURCHASE;

    @JsonCreator
    public static TransactionType fromString(String value) {
        try {
            return TransactionType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new DataValidationException("Transaction type '" + value + "' not found");
        }
    }
}
