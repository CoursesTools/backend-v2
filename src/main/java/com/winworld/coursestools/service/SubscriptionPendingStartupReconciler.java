package com.winworld.coursestools.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionPendingStartupReconciler {
    private final PendingSubscriptionReconciliationService reconciliationService;

    @EventListener(ApplicationReadyEvent.class)
    public void reconcileOnStartup() {
        log.info("Running stuck PENDING subscription reconciliation on application startup");
        reconciliationService.reconcileStuckPendingSubscriptions("startup");
    }
}
