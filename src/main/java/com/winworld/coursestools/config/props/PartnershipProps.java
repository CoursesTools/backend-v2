package com.winworld.coursestools.config.props;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@ConfigurationProperties(prefix = "partnership")
@RequiredArgsConstructor
@Getter
public class PartnershipProps {
    private final List<Level> levels;
    private final Discount discount;

    @PostConstruct
    public void sortLevelsByRank() {
        if (levels != null) {
            levels.sort(Comparator.comparingInt(Level::getRank));
        }
    }

    @Data
    public static class Level {
        private int rank;
        private String name;
        private int requiredReferrals;
        private BigDecimal cashback1;
        private BigDecimal cashback2;
        private BigDecimal cashback3;
        private BigDecimal cashback4;
        private BigDecimal cashback5;
        private BigDecimal cashback6;

        public BigDecimal getCashback(int num) {
            return switch (num) {
                case 1 -> cashback1;
                case 2 -> cashback2;
                case 3 -> cashback3;
                case 4 -> cashback4;
                case 5 -> cashback5;
                case 6 -> cashback6;
                default -> throw new IllegalStateException("Unexpected value: " + num);
            };
        }
    }

    @Data
    public static class Discount {
        private int month;
        private int year;
        private int lifetime;
    }
}
