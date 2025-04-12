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
    private final BigDecimal discount;

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

        public BigDecimal getCashback(int num) {
            return switch (num) {
                case 1 -> cashback1;
                case 2 -> cashback2;
                default -> throw new IllegalStateException("Unexpected value: " + num);
            };
        }
    }
}
