package com.winworld.coursestools.config;

import com.winworld.coursestools.service.SubscriptionStateReconciliationService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("subscriptionPastGracePeriod")
@RequiredArgsConstructor
public class SubscriptionPastGracePeriodHealthIndicator implements HealthIndicator {
    private final SubscriptionStateReconciliationService subscriptionStateReconciliationService;

    @Override
    public Health health() {
        long staleSubscriptions = subscriptionStateReconciliationService.countSubscriptionsPastGracePeriod();
        if (staleSubscriptions == 0) {
            return Health.up()
                    .withDetail("staleSubscriptionsPastGracePeriod", 0)
                    .build();
        }

        return Health.down()
                .withDetail("staleSubscriptionsPastGracePeriod", staleSubscriptions)
                .withDetail("reason", "Subscriptions remained non-terminated after the 7-day grace period")
                .build();
    }
}
