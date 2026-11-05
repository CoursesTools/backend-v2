package com.winworld.coursestools.service;

import com.winworld.coursestools.entity.user.UserSubscription;
import com.winworld.coursestools.enums.Plan;
import com.winworld.coursestools.repository.user.UserSubscriptionRepository;
import com.winworld.coursestools.service.ops.SystemSettingsService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.winworld.coursestools.enums.SubscriptionStatus.TERMINATED;

@Service
@Slf4j
public class SubscriptionStateReconciliationService {
    private static final String PAST_GRACE_GAUGE_NAME = "subscriptions.past_grace_period.count";
    private static final EnumSet<Plan> PAST_GRACE_RECONCILIATION_PLANS = EnumSet.of(Plan.MONTH, Plan.YEAR);

    private final UserSubscriptionRepository userSubscriptionRepository;
    private final SubscriptionDeactivationService subscriptionDeactivationService;
    private final SystemSettingsService systemSettingsService;

    public SubscriptionStateReconciliationService(
            UserSubscriptionRepository userSubscriptionRepository,
            SubscriptionDeactivationService subscriptionDeactivationService,
            SystemSettingsService systemSettingsService,
            MeterRegistry meterRegistry
    ) {
        this.userSubscriptionRepository = userSubscriptionRepository;
        this.subscriptionDeactivationService = subscriptionDeactivationService;
        this.systemSettingsService = systemSettingsService;

        Gauge.builder(PAST_GRACE_GAUGE_NAME, this, service -> service.countSubscriptionsPastGracePeriod())
                .description("Non-trial month/year subscriptions that are still not terminated after the grace period expired")
                .register(meterRegistry);
    }

    public Optional<UserSubscription> discardPastGracePeriodSubscription(UserSubscription userSubscription) {
        if (userSubscription == null || !isPastGracePeriod(userSubscription)) {
            return Optional.ofNullable(userSubscription);
        }

        log.warn(
                "Subscription {} for user {} is past grace period with status {}. Terminating it before continuing.",
                userSubscription.getId(),
                userSubscription.getUser().getId(),
                userSubscription.getStatus()
        );
        subscriptionDeactivationService.terminatePastGracePeriodSubscription(userSubscription.getId());
        return Optional.empty();
    }

    /** @return users whose subscription this run terminated (their alerts can now be removed). */
    public List<Integer> reconcilePastGracePeriodSubscriptions(String trigger) {
        LocalDateTime cutoffDate = getPastGraceCutoff();
        List<Integer> subscriptionIds = userSubscriptionRepository
                .findAllNonTerminatedPastGracePeriod(cutoffDate, PAST_GRACE_RECONCILIATION_PLANS)
                .stream()
                .map(UserSubscription::getId)
                .toList();

        if (subscriptionIds.isEmpty()) {
            log.info("Past-grace reconciliation via {} found no stale subscriptions", trigger);
            return List.of();
        }

        log.warn(
                "Past-grace reconciliation via {} found {} stale subscriptions that must be terminated",
                trigger,
                subscriptionIds.size()
        );

        List<Integer> terminatedUserIds = new ArrayList<>();
        for (Integer subscriptionId : subscriptionIds) {
            try {
                subscriptionDeactivationService.terminatePastGracePeriodSubscription(subscriptionId)
                        .ifPresent(terminatedUserIds::add);
            } catch (Exception e) {
                log.error(
                        "Failed to terminate subscription {} during past-grace reconciliation via {}",
                        subscriptionId,
                        trigger,
                        e
                );
            }
        }

        long remaining = countSubscriptionsPastGracePeriod();
        if (remaining > 0) {
            log.error(
                    "Past-grace reconciliation via {} finished with {} stale subscriptions still not terminated",
                    trigger,
                    remaining
            );
        } else {
            log.info(
                    "Past-grace reconciliation via {} terminated {} stale subscriptions",
                    trigger,
                    terminatedUserIds.size()
            );
        }
        return terminatedUserIds;
    }

    @Transactional(readOnly = true)
    public long countSubscriptionsPastGracePeriod() {
        return userSubscriptionRepository.countAllNonTerminatedPastGracePeriod(
                getPastGraceCutoff(),
                PAST_GRACE_RECONCILIATION_PLANS
        );
    }

    public boolean isPastGracePeriod(UserSubscription userSubscription) {
        if (userSubscription == null
                || userSubscription.getIsTrial()
                || userSubscription.getStatus() == TERMINATED
                || userSubscription.getPlan() == null
                || !PAST_GRACE_RECONCILIATION_PLANS.contains(userSubscription.getPlan().getName())) {
            return false;
        }
        return !userSubscription.getExpiredAt().isAfter(getPastGraceCutoff());
    }

    private LocalDateTime getPastGraceCutoff() {
        return LocalDateTime.now(ZoneOffset.UTC).minusDays(systemSettingsService.getGracePeriodDays());
    }
}
