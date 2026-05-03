package com.winworld.coursestools.service;

import com.winworld.coursestools.entity.Order;
import com.winworld.coursestools.entity.Referral;
import com.winworld.coursestools.entity.subscription.SubscriptionPlan;
import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.entity.user.UserSubscription;
import com.winworld.coursestools.dto.payment.StripeSubscriptionLifecycleDto;
import com.winworld.coursestools.enums.OrderStatus;
import com.winworld.coursestools.enums.OrderType;
import com.winworld.coursestools.enums.PaymentMethod;
import com.winworld.coursestools.enums.Plan;
import com.winworld.coursestools.enums.SubscriptionEventType;
import com.winworld.coursestools.enums.SubscriptionStatus;
import com.winworld.coursestools.event.SubscriptionChangeStatusEvent;
import com.winworld.coursestools.mapper.SubscriptionMapper;
import com.winworld.coursestools.mapper.UserMapper;
import com.winworld.coursestools.repository.TrialActivationRepository;
import com.winworld.coursestools.repository.subscription.SubscriptionPlanRepository;
import com.winworld.coursestools.repository.subscription.SubscriptionTypeRepository;
import com.winworld.coursestools.repository.user.UserSubscriptionRepository;
import com.winworld.coursestools.service.external.ActivatingSubscriptionService;
import com.winworld.coursestools.service.payment.impl.StripePaymentService;
import com.winworld.coursestools.service.user.UserDataService;
import com.winworld.coursestools.service.user.UserSubscriptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.winworld.coursestools.service.payment.impl.StripePaymentService.CURRENT_PERIOD_END;
import static com.winworld.coursestools.service.payment.impl.StripePaymentService.CANCEL_AT_PERIOD_END;
import static com.winworld.coursestools.service.payment.impl.StripePaymentService.STRIPE_STATUS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    private UserDataService userDataService;

    @Mock
    private UserMapper userMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private SubscriptionMapper subscriptionMapper;

    @Mock
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Mock
    private SubscriptionTypeRepository subscriptionTypeRepository;

    @Mock
    private UserSubscriptionService userSubscriptionService;

    @Mock
    private StripePaymentService stripePaymentService;

    @Mock
    private ActivatingSubscriptionService activatingSubscriptionService;

    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;

    @Mock
    private TrialActivationRepository trialActivationRepository;

    @Mock
    private SubscriptionDeactivationService subscriptionDeactivationService;

    @Mock
    private SubscriptionStateReconciliationService subscriptionStateReconciliationService;

    @InjectMocks
    private SubscriptionService subscriptionService;

    @Test
    void updateUserSubscriptionAfterPayment_usesStripeCurrentPeriodEndSecondForSecond() {
        long currentPeriodEnd = 1777698505L;
        LocalDateTime expectedExpiration = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(currentPeriodEnd),
                ZoneOffset.UTC
        );

        User user = new User();
        user.setId(1);

        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setName(Plan.MONTH);
        plan.setDurationDays(30);
        plan.setPrice(new BigDecimal("29.99"));

        UserSubscription existingSubscription = UserSubscription.builder()
                .id(10)
                .user(user)
                .plan(plan)
                .status(SubscriptionStatus.GRANTED)
                .paymentMethod(PaymentMethod.STRIPE)
                .price(new BigDecimal("29.99"))
                .isTrial(false)
                .expiredAt(LocalDateTime.of(2026, 5, 2, 0, 0))
                .paymentProviderData(Map.of())
                .build();

        Order order = Order.builder()
                .id(698)
                .user(user)
                .plan(plan)
                .originalPrice(new BigDecimal("29.99"))
                .totalPrice(new BigDecimal("29.99"))
                .paymentMethod(PaymentMethod.STRIPE)
                .orderType(OrderType.RECURRENT)
                .status(OrderStatus.PAID)
                .build();

        SubscriptionChangeStatusEvent event = new SubscriptionChangeStatusEvent();
        when(subscriptionMapper.toEvent(user, SubscriptionEventType.EXTENDED, existingSubscription))
                .thenReturn(event);

        subscriptionService.updateUserSubscriptionAfterPayment(
                existingSubscription,
                order,
                user,
                Map.of(CURRENT_PERIOD_END, currentPeriodEnd)
        );

        assertEquals(expectedExpiration, existingSubscription.getExpiredAt());
        verify(eventPublisher).publishEvent(event);
    }

    @Test
    void updateUserSubscriptionAfterPayment_cancelsStripeWhenGraceSubscriptionRestoredWithNonStripePayment() {
        User user = new User();
        user.setId(2);

        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setName(Plan.MONTH);
        plan.setDurationDays(30);
        plan.setPrice(new BigDecimal("29.99"));

        UserSubscription existingSubscription = UserSubscription.builder()
                .id(11)
                .user(user)
                .plan(plan)
                .status(SubscriptionStatus.GRACE_PERIOD)
                .paymentMethod(PaymentMethod.STRIPE)
                .price(new BigDecimal("29.99"))
                .isTrial(false)
                .expiredAt(LocalDateTime.of(2026, 5, 2, 5, 8, 25))
                .paymentProviderData(Map.of(StripePaymentService.SUBSCRIPTION_ID, "sub_old"))
                .build();

        Order order = Order.builder()
                .id(699)
                .user(user)
                .plan(plan)
                .originalPrice(new BigDecimal("29.99"))
                .totalPrice(new BigDecimal("29.99"))
                .paymentMethod(PaymentMethod.CRYPTO)
                .orderType(OrderType.ONE_TIME)
                .status(OrderStatus.PAID)
                .build();

        SubscriptionChangeStatusEvent event = new SubscriptionChangeStatusEvent();
        when(subscriptionMapper.toEvent(user, SubscriptionEventType.RESTORED, existingSubscription))
                .thenReturn(event);

        LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC).plusDays(30);
        subscriptionService.updateUserSubscriptionAfterPayment(existingSubscription, order, user, Map.of());
        LocalDateTime after = LocalDateTime.now(ZoneOffset.UTC).plusDays(30);

        assertEquals(PaymentMethod.CRYPTO, existingSubscription.getPaymentMethod());
        assertFalse(existingSubscription.getExpiredAt().isBefore(before));
        assertFalse(existingSubscription.getExpiredAt().isAfter(after));
        verify(stripePaymentService).cancelSubscription(existingSubscription);
        verify(eventPublisher).publishEvent(event);
    }

    @Test
    void updateUserSubscriptionAfterPayment_newNonStripeSubscriptionUsesPlanDurationWithoutPaymentGrace() {
        LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC).plusDays(30);
        User user = new User();
        user.setId(3);
        user.setSubscriptions(new ArrayList<>());

        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setName(Plan.MONTH);
        plan.setDurationDays(30);
        plan.setPrice(new BigDecimal("29.99"));

        Order order = Order.builder()
                .id(700)
                .user(user)
                .plan(plan)
                .originalPrice(new BigDecimal("29.99"))
                .totalPrice(new BigDecimal("29.99"))
                .paymentMethod(PaymentMethod.CRYPTO)
                .orderType(OrderType.ONE_TIME)
                .status(OrderStatus.PAID)
                .build();

        when(userSubscriptionService.save(any(UserSubscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(subscriptionMapper.toEvent(eq(user), eq(SubscriptionEventType.CREATED), any(UserSubscription.class)))
                .thenReturn(new SubscriptionChangeStatusEvent());

        subscriptionService.updateUserSubscriptionAfterPayment(null, order, user, Map.of());

        UserSubscription created = user.getSubscriptions().get(0);
        LocalDateTime after = LocalDateTime.now(ZoneOffset.UTC).plusDays(30);
        assertFalse(created.getExpiredAt().isBefore(before));
        assertFalse(created.getExpiredAt().isAfter(after));
    }

    @Test
    void updateUserSubscriptionAfterPayment_activeNonStripeRenewalExtendsFromExistingExpiry() {
        User user = new User();
        user.setId(4);

        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setName(Plan.MONTH);
        plan.setDurationDays(30);
        plan.setPrice(new BigDecimal("29.99"));

        UserSubscription existingSubscription = UserSubscription.builder()
                .id(12)
                .user(user)
                .plan(plan)
                .status(SubscriptionStatus.GRANTED)
                .paymentMethod(PaymentMethod.CRYPTO)
                .price(new BigDecimal("29.99"))
                .isTrial(false)
                .expiredAt(LocalDateTime.of(2026, 5, 31, 22, 0))
                .paymentProviderData(Map.of())
                .build();

        Order order = Order.builder()
                .id(701)
                .user(user)
                .plan(plan)
                .originalPrice(new BigDecimal("29.99"))
                .totalPrice(new BigDecimal("29.99"))
                .paymentMethod(PaymentMethod.CRYPTO)
                .orderType(OrderType.ONE_TIME)
                .status(OrderStatus.PAID)
                .build();

        when(subscriptionMapper.toEvent(user, SubscriptionEventType.EXTENDED, existingSubscription))
                .thenReturn(new SubscriptionChangeStatusEvent());

        subscriptionService.updateUserSubscriptionAfterPayment(existingSubscription, order, user, Map.of());

        assertEquals(LocalDateTime.of(2026, 6, 30, 22, 0), existingSubscription.getExpiredAt());
    }

    @Test
    void syncStripeSubscriptionUpdated_updatesPeriodEndAndStripeMetadata() {
        long currentPeriodEnd = 1777698505L;
        UserSubscription subscription = UserSubscription.builder()
                .id(13)
                .status(SubscriptionStatus.GRANTED)
                .paymentMethod(PaymentMethod.STRIPE)
                .isTrial(false)
                .expiredAt(LocalDateTime.of(2026, 5, 2, 0, 0))
                .paymentProviderData(new HashMap<>(Map.of(StripePaymentService.SUBSCRIPTION_ID, "sub_sync")))
                .build();

        when(userSubscriptionRepository.findByStripeSubscriptionId("sub_sync")).thenReturn(Optional.of(subscription));

        subscriptionService.syncStripeSubscriptionUpdated(StripeSubscriptionLifecycleDto.builder()
                .subscriptionId("sub_sync")
                .currentPeriodEnd(currentPeriodEnd)
                .status("active")
                .cancelAtPeriodEnd(false)
                .build());

        assertEquals(LocalDateTime.ofInstant(Instant.ofEpochSecond(currentPeriodEnd), ZoneOffset.UTC),
                subscription.getExpiredAt());
        assertEquals(currentPeriodEnd, subscription.getPaymentProviderData().get(CURRENT_PERIOD_END));
        assertEquals("active", subscription.getPaymentProviderData().get(STRIPE_STATUS));
        assertEquals(false, subscription.getPaymentProviderData().get(CANCEL_AT_PERIOD_END));
        verify(userSubscriptionService).save(subscription);
    }

    @Test
    void handleStripeSubscriptionDeleted_terminatesLocalSubscriptionWithoutCancelingStripe() {
        User user = new User();
        user.setId(5);
        Referral referral = new Referral();
        referral.setActive(true);
        user.setReferred(referral);

        UserSubscription subscription = UserSubscription.builder()
                .id(14)
                .user(user)
                .status(SubscriptionStatus.GRANTED)
                .paymentMethod(PaymentMethod.STRIPE)
                .isTrial(false)
                .expiredAt(LocalDateTime.of(2026, 5, 2, 5, 8, 25))
                .paymentProviderData(new HashMap<>(Map.of(StripePaymentService.SUBSCRIPTION_ID, "sub_deleted")))
                .build();

        SubscriptionChangeStatusEvent event = new SubscriptionChangeStatusEvent();
        when(userSubscriptionRepository.findByStripeSubscriptionId("sub_deleted")).thenReturn(Optional.of(subscription));
        when(subscriptionMapper.toEvent(user, SubscriptionEventType.GRACE_PERIOD_END, subscription)).thenReturn(event);

        subscriptionService.handleStripeSubscriptionDeleted(StripeSubscriptionLifecycleDto.builder()
                .subscriptionId("sub_deleted")
                .currentPeriodEnd(1777698505L)
                .status("canceled")
                .cancelAtPeriodEnd(false)
                .build());

        assertEquals(SubscriptionStatus.TERMINATED, subscription.getStatus());
        assertFalse(referral.isActive());
        assertEquals("canceled", subscription.getPaymentProviderData().get(STRIPE_STATUS));
        verify(stripePaymentService, never()).cancelSubscription(subscription);
        verify(eventPublisher).publishEvent(event);
    }

    @Test
    void handleStripeSubscriptionDeleted_ignoresSubscriptionAlreadyConvertedAwayFromStripe() {
        User user = new User();
        user.setId(6);
        Referral referral = new Referral();
        referral.setActive(true);
        user.setReferred(referral);

        UserSubscription subscription = UserSubscription.builder()
                .id(15)
                .user(user)
                .status(SubscriptionStatus.GRANTED)
                .paymentMethod(PaymentMethod.MANUAL)
                .isTrial(false)
                .expiredAt(LocalDateTime.of(2100, 12, 31, 23, 59, 59))
                .paymentProviderData(new HashMap<>(Map.of(StripePaymentService.SUBSCRIPTION_ID, "sub_old")))
                .build();

        when(userSubscriptionRepository.findByStripeSubscriptionId("sub_old")).thenReturn(Optional.of(subscription));

        subscriptionService.handleStripeSubscriptionDeleted(StripeSubscriptionLifecycleDto.builder()
                .subscriptionId("sub_old")
                .currentPeriodEnd(1777698505L)
                .status("canceled")
                .cancelAtPeriodEnd(false)
                .build());

        assertEquals(SubscriptionStatus.GRANTED, subscription.getStatus());
        assertTrue(referral.isActive());
        verify(userSubscriptionService, never()).save(subscription);
        verify(stripePaymentService, never()).cancelSubscription(subscription);
        verify(eventPublisher, never()).publishEvent(any());
    }
}
