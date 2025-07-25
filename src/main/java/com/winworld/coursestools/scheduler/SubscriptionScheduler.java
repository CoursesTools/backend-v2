package com.winworld.coursestools.scheduler;

import com.winworld.coursestools.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionScheduler {
    private final SubscriptionService subscriptionService;

    //TODO последить за кол-вом пользователей, если будет много, то нужно будет оптимизировать

//    @Scheduled(cron = "${scheduler.subscription.expired-subscriptions}")
    public void deactivateExpiredSubscriptions() {
        log.info("Deactivating expired subscriptions job start");
        subscriptionService.deactivateExpiredSubscriptions();
        log.info("Deactivating expired subscriptions job end");
    }

//    @Scheduled(cron = "${scheduler.subscription.trial-expired-subscriptions}")
    public void cleanupExpiredTrialSubscriptions() {
        log.info("Deactivating trial expired subscriptions job start");
        subscriptionService.deactivateExpiredTrialSubscriptions();
        log.info("Deactivating trial expired subscriptions job end");
    }

//    @Scheduled(cron = "${scheduler.subscription.grace-period-expired-subscriptions}")
    public void cleanupExpiredGracePeriodSubscriptions() {
        log.info("Deactivating expired subscriptions with grace period job start");
        subscriptionService.deactivateExpiredGracePeriodSubscriptions();
        log.info("Deactivating expired subscriptions with grace period job end");
    }
}
