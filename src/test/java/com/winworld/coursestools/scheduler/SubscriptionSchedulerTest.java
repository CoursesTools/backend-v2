package com.winworld.coursestools.scheduler;

import com.winworld.coursestools.service.AlertService;
import com.winworld.coursestools.service.PendingSubscriptionReconciliationService;
import com.winworld.coursestools.service.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionSchedulerTest {

    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private PendingSubscriptionReconciliationService pendingSubscriptionReconciliationService;
    @Mock
    private AlertService alertService;

    @InjectMocks
    private SubscriptionScheduler scheduler;

    @Test
    void enteringGracePausesAlertsWithoutTouchingTheAlertList() {
        when(subscriptionService.deactivateExpiredSubscriptions()).thenReturn(List.of(7, 8));

        scheduler.deactivateExpiredSubscriptions();

        verify(alertService, never()).removeAlertsIfNoActiveSubscription(anyInt());
        verify(alertService, never()).unSubscribeOnAllAlerts(anyInt());
    }

    @Test
    void fullExpiryAfterGraceRemovesTheAlertList() {
        when(subscriptionService.deactivateExpiredGracePeriodSubscriptions()).thenReturn(List.of(5, 6));

        scheduler.cleanupExpiredGracePeriodSubscriptions();

        verify(alertService).removeAlertsIfNoActiveSubscription(5);
        verify(alertService).removeAlertsIfNoActiveSubscription(6);
    }

    @Test
    void endedTrialRemovesTheAlertListUnlessAnotherSubscriptionIsActive() {
        when(subscriptionService.deactivateExpiredTrialSubscriptions()).thenReturn(List.of(9));

        scheduler.cleanupExpiredTrialSubscriptions();

        verify(alertService).removeAlertsIfNoActiveSubscription(9);
        verify(alertService, never()).unSubscribeOnAllAlerts(anyInt());
    }
}
