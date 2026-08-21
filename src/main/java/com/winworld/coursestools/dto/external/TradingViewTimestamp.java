package com.winworld.coursestools.dto.external;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/** Stable precision for every expiration sent to the TradingView access server. */
final class TradingViewTimestamp {
    private TradingViewTimestamp() {
    }

    static LocalDateTime wholeSeconds(LocalDateTime value) {
        return value == null ? null : value.truncatedTo(ChronoUnit.SECONDS);
    }
}
