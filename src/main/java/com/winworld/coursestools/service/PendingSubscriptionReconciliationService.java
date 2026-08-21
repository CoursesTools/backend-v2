package com.winworld.coursestools.service;

import com.winworld.coursestools.repository.user.UserSubscriptionRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@Slf4j
public class PendingSubscriptionReconciliationService {
    private static final String STUCK_PENDING_GAUGE_NAME = "subscriptions.stuck_pending.count";
    private static final int MINIMUM_RECONCILIATION_AGE_MINUTES = 1;

    private final UserSubscriptionRepository userSubscriptionRepository;
    private final PendingSubscriptionReconciliationWorker worker;
    private final int minimumAgeMinutes;

    public PendingSubscriptionReconciliationService(
            UserSubscriptionRepository userSubscriptionRepository,
            PendingSubscriptionReconciliationWorker worker,
            MeterRegistry meterRegistry,
            @Value("${tradingview.pending-reconciliation-min-age-minutes:15}") int minimumAgeMinutes
    ) {
        this.userSubscriptionRepository = userSubscriptionRepository;
        this.worker = worker;
        this.minimumAgeMinutes = Math.max(minimumAgeMinutes, MINIMUM_RECONCILIATION_AGE_MINUTES);
        Gauge.builder(STUCK_PENDING_GAUGE_NAME, this, service -> service.countStuckPendingSubscriptions())
                .description("PENDING subscriptions older than the activation reconciliation cutoff")
                .register(meterRegistry);
    }

    public int reconcileStuckPendingSubscriptions(String trigger) {
        LocalDateTime cutoffDate = cutoffDate();
        List<Integer> subscriptionIds = userSubscriptionRepository
                .findStuckPendingSubscriptionIds(cutoffDate);
        if (subscriptionIds.isEmpty()) {
            log.info("Stuck PENDING reconciliation via {} found no candidates", trigger);
            return 0;
        }

        log.warn(
                "Stuck PENDING reconciliation via {} found {} candidates",
                trigger,
                subscriptionIds.size()
        );
        int restaged = 0;
        for (Integer subscriptionId : subscriptionIds) {
            try {
                if (worker.reconcile(subscriptionId, cutoffDate, trigger)) {
                    restaged++;
                }
            } catch (Exception e) {
                log.error(
                        "Failed to reconcile stuck PENDING subscription {} via {}",
                        subscriptionId,
                        trigger,
                        e
                );
            }
        }
        log.info(
                "Stuck PENDING reconciliation via {} re-staged {} of {} candidates",
                trigger,
                restaged,
                subscriptionIds.size()
        );
        return restaged;
    }

    @Transactional(readOnly = true)
    public long countStuckPendingSubscriptions() {
        return userSubscriptionRepository.countStuckPendingSubscriptions(cutoffDate());
    }

    private LocalDateTime cutoffDate() {
        return LocalDateTime.now(ZoneOffset.UTC)
                .plusNanos(500)
                .truncatedTo(ChronoUnit.MICROS)
                .minusMinutes(minimumAgeMinutes);
    }
}
