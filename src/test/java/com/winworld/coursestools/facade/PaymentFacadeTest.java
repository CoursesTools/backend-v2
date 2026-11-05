package com.winworld.coursestools.facade;

import com.winworld.coursestools.dto.payment.StripeRetrieveDto;
import com.winworld.coursestools.dto.payment.StripeSubscriptionLifecycleDto;
import com.winworld.coursestools.service.AlertService;
import com.winworld.coursestools.service.SubscriptionService;
import com.winworld.coursestools.service.payment.impl.StripePaymentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentFacadeTest {

    @Mock
    private StripePaymentService stripePaymentService;

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private AlertService alertService;

    @InjectMocks
    private PaymentFacade paymentFacade;

    private final StripeSubscriptionLifecycleDto lifecycleDto = StripeSubscriptionLifecycleDto.builder()
            .subscriptionId("sub_cancelled")
            .status("canceled")
            .build();

    @Test
    void stripeCancellationRemovesAlertsOnceNoActiveSubscriptionRemains() {
        when(stripePaymentService.isSubscriptionLifecycleEvent(any(StripeRetrieveDto.class))).thenReturn(true);
        when(stripePaymentService.processSubscriptionLifecycleWebhook(any(StripeRetrieveDto.class))).thenReturn(lifecycleDto);
        when(stripePaymentService.isSubscriptionDeletedEvent(any(StripeRetrieveDto.class))).thenReturn(true);
        when(subscriptionService.handleStripeSubscriptionDeleted(lifecycleDto)).thenReturn(Optional.of(42));

        paymentFacade.retrieveStripePayment("{}", "sig");

        verify(alertService).removeAlertsIfNoActiveSubscription(42);
    }

    @Test
    void deletionThatDoesNotApplyLeavesAlertsAlone() {
        when(stripePaymentService.isSubscriptionLifecycleEvent(any(StripeRetrieveDto.class))).thenReturn(true);
        when(stripePaymentService.processSubscriptionLifecycleWebhook(any(StripeRetrieveDto.class))).thenReturn(lifecycleDto);
        when(stripePaymentService.isSubscriptionDeletedEvent(any(StripeRetrieveDto.class))).thenReturn(true);
        when(subscriptionService.handleStripeSubscriptionDeleted(lifecycleDto)).thenReturn(Optional.empty());

        paymentFacade.retrieveStripePayment("{}", "sig");

        verify(alertService, never()).removeAlertsIfNoActiveSubscription(anyInt());
    }

    @Test
    void subscriptionUpdateNeverTouchesAlerts() {
        when(stripePaymentService.isSubscriptionLifecycleEvent(any(StripeRetrieveDto.class))).thenReturn(true);
        when(stripePaymentService.processSubscriptionLifecycleWebhook(any(StripeRetrieveDto.class))).thenReturn(lifecycleDto);
        when(stripePaymentService.isSubscriptionDeletedEvent(any(StripeRetrieveDto.class))).thenReturn(false);

        paymentFacade.retrieveStripePayment("{}", "sig");

        verify(subscriptionService).syncStripeSubscriptionUpdated(lifecycleDto);
        verify(alertService, never()).removeAlertsIfNoActiveSubscription(anyInt());
    }
}
