package com.winworld.coursestools.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionPastGracePeriodStartupReconciler {
    private final SubscriptionStateReconciliationService subscriptionStateReconciliationService;

    @EventListener(ApplicationReadyEvent.class)
    public void reconcileOnStartup() {
        log.info("Running past-grace subscription reconciliation on application startup");
        subscriptionStateReconciliationService.reconcilePastGracePeriodSubscriptions("startup");
    }
}
