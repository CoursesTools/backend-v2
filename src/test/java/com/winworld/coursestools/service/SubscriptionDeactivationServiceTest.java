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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

        when(userSubscriptionRepository.findByIdWithUserDetails(42)).thenReturn(Optional.of(userSubscription));
        when(subscriptionMapper.toEvent(user, SubscriptionEventType.GRACE_PERIOD_END, userSubscription))
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
                .expiredAt(LocalDateTime.now().minusMinutes(1))
                .paymentProviderData(new java.util.HashMap<>())
                .build();

        SubscriptionChangeStatusEvent event = new SubscriptionChangeStatusEvent();

        when(userSubscriptionRepository.findByIdWithUserDetails(84)).thenReturn(Optional.of(userSubscription));
        when(subscriptionMapper.toEvent(user, SubscriptionEventType.GRACE_PERIOD_START, userSubscription))
                .thenReturn(event);

        subscriptionDeactivationService.deactivateSingleSubscription(84);

        assertEquals(SubscriptionStatus.GRACE_PERIOD, userSubscription.getStatus());
        verify(stripePaymentService, never()).cancelSubscription(userSubscription);
        verify(eventPublisher).publishEvent(event);
    }
}
