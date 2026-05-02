package com.winworld.coursestools.service;

import com.winworld.coursestools.entity.Order;
import com.winworld.coursestools.entity.subscription.SubscriptionPlan;
import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.entity.user.UserSubscription;
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
import java.util.Map;

import static com.winworld.coursestools.service.payment.impl.StripePaymentService.CURRENT_PERIOD_END;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
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

        subscriptionService.updateUserSubscriptionAfterPayment(existingSubscription, order, user, Map.of());

        assertEquals(PaymentMethod.CRYPTO, existingSubscription.getPaymentMethod());
        verify(stripePaymentService).cancelSubscription(existingSubscription);
        verify(eventPublisher).publishEvent(event);
    }
}
