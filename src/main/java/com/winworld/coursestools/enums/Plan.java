package com.winworld.coursestools.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum Plan {
    MONTH(1), YEAR(12), LIFETIME(999), TRIAL(0);

    private final int value;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static Plan fromString(String value) {
        return Plan.valueOf(value.toUpperCase());
    }
}
