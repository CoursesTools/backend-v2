package com.winworld.coursestools.service.ops.checks;

import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Shared helpers for the ops health checks. */
@Slf4j
final class OpsChecks {
    private static final DateTimeFormatter UTC_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private OpsChecks() {
    }

    @FunctionalInterface
    interface Check {
        void run() throws Exception;
    }

    /** Runs one check; a failure of the check itself is logged and never stops the other checks. */
    static void safely(String name, Check check) {
        try {
            check.run();
        } catch (Exception e) {
            log.error("Ops check '{}' could not run: {}", name, e.getMessage(), e);
        }
    }

    static String utc(long epochSecond) {
        return UTC_FORMAT.format(Instant.ofEpochSecond(epochSecond));
    }

    static String money(Long cents, String currency) {
        if (cents == null) {
            return "?";
        }
        String amount = BigDecimal.valueOf(cents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP).toPlainString();
        return currency == null ? amount : amount + " " + currency.toUpperCase(Locale.ROOT);
    }
}
