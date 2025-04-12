package com.winworld.coursestools.enums;

public enum DiscountType {
    PERCENTAGE, FIXED;

    public boolean isPercentage() {
        return this == PERCENTAGE;
    }

    public boolean isFixed() {
        return this == FIXED;
    }
}
