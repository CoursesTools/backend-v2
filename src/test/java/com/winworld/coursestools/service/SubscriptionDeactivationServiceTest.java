package com.winworld.coursestools.service;

import com.winworld.coursestools.entity.Referral;
import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.entity.user.UserSubscription;
import com.winworld.coursestools.enums.PaymentMethod;
import com.winworld.coursestools.enums.SubscriptionEventType;
import com.winworld.coursestools.enums.SubscriptionStatus;
import com.winworld.coursestools.event.SubscriptionChangeStatusEvent;
import com.winworld.coursestools.mapper.SubscriptionMapper;
import com.winworld.coursestools.repository.user.UserSubscriptionRepository;
import com.winworld.coursestools.service.payment.impl.StripePaymentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static com.winworld.coursestools.enums.TradingViewExpirationPolicy.EXACT;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

@ExtendWith(MockitoExtension.class)
class SubscriptionDeactivationServiceTest {

    @Mock
    private StripePaymentService stripePaymentService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private SubscriptionMapper subscriptionMapper;

    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;

    @InjectMocks
    private SubscriptionDeactivationService subscriptionDeactivationService;

    @Test
    void terminatePastGracePeriodSubscription_marksSubscriptionTerminatedAndPublishesEvent() {
        User user = new User();
        user.setId(101);

        Referral referral = new Referral();
        referral.setActive(true);
        user.setReferred(referral);

        UserSubscription userSubscription = UserSubscription.builder()
                .id(42)
                .user(user)
                .paymentMethod(PaymentMethod.STRIPE)
                .status(SubscriptionStatus.GRACE_PERIOD)
                .isTrial(false)
                .expiredAt(LocalDateTime.now().minusDays(30))
                .paymentProviderData(new java.util.HashMap<>())
                .build();

        SubscriptionChangeStatusEvent event = new SubscriptionChangeStatusEvent();

        when(userSubscriptionRepository.findByIdWithUserDetailsForUpdate(42)).thenReturn(Optional.of(userSubscription));
        when(subscriptionMapper.toEvent(user, SubscriptionEventType.GRACE_PERIOD_END, userSubscription, EXACT))
                .thenReturn(event);

        subscriptionDeactivationService.terminatePastGracePeriodSubscription(42);

        assertEquals(SubscriptionStatus.TERMINATED, userSubscription.getStatus());
        assertFalse(referral.isActive());
        verify(stripePaymentService, never()).cancelSubscription(userSubscription);
        verify(eventPublisher).publishEvent(event);
    }

    @Test
    void deactivateSingleSubscription_movesStripeSubscriptionToGraceButDoesNotCancelStripe() {
        User user = new User();
        user.setId(202);

        UserSubscription userSubscription = UserSubscription.builder()
                .id(84)
                .user(user)
                .paymentMethod(PaymentMethod.STRIPE)
                .status(SubscriptionStatus.GRANTED)
                .isTrial(false)
                .expiredAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1))
                .paymentProviderData(new java.util.HashMap<>())
                .build();

        SubscriptionChangeStatusEvent event = new SubscriptionChangeStatusEvent();

        when(userSubscriptionRepository.findByIdWithUserDetailsForUpdate(84)).thenReturn(Optional.of(userSubscription));
        when(subscriptionMapper.toEvent(user, SubscriptionEventType.GRACE_PERIOD_START, userSubscription, EXACT))
                .thenReturn(event);

        assertTrue(subscriptionDeactivationService.deactivateSingleSubscription(84));

        assertEquals(SubscriptionStatus.GRACE_PERIOD, userSubscription.getStatus());
        verify(stripePaymentService, never()).cancelSubscription(userSubscription);
        verify(eventPublisher).publishEvent(event);
    }

    @Test
    void deactivateSingleSubscription_skipsTrialEvenIfPassedAsStaleCandidate() {
        UserSubscription trial = UserSubscription.builder()
                .id(85)
                .isTrial(true)
                .status(SubscriptionStatus.GRANTED)
                .expiredAt(LocalDateTime.now(ZoneOffset.UTC).minusDays(1))
                .build();
        when(userSubscriptionRepository.findByIdWithUserDetailsForUpdate(85)).thenReturn(Optional.of(trial));

        assertFalse(subscriptionDeactivationService.deactivateSingleSubscription(85));

        assertEquals(SubscriptionStatus.GRANTED, trial.getStatus());
        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deactivateSingleSubscription_skipsRenewedCandidateAfterLockedRevalidation() {
        UserSubscription renewed = UserSubscription.builder()
                .id(86)
                .isTrial(false)
                .status(SubscriptionStatus.GRANTED)
                .expiredAt(LocalDateTime.now(ZoneOffset.UTC).plusDays(30))
                .build();
        when(userSubscriptionRepository.findByIdWithUserDetailsForUpdate(86)).thenReturn(Optional.of(renewed));

        assertFalse(subscriptionDeactivationService.deactivateSingleSubscription(86));

        assertEquals(SubscriptionStatus.GRANTED, renewed.getStatus());
        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }
}
