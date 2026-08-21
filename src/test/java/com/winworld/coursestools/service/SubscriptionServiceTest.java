package com.winworld.coursestools.service;

import com.winworld.coursestools.entity.Order;
import com.winworld.coursestools.entity.Referral;
import com.winworld.coursestools.entity.TradingViewRetryJob;
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
import com.winworld.coursestools.service.external.TradingViewRetryService;
import com.winworld.coursestools.service.payment.impl.StripePaymentService;
import com.winworld.coursestools.service.user.UserDataService;
import com.winworld.coursestools.service.user.UserSubscriptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.mockito.InOrder;
import com.winworld.coursestools.entity.subscription.SubscriptionType;
import com.winworld.coursestools.entity.user.UserSocial;
import com.winworld.coursestools.enums.SubscriptionName;
import com.winworld.coursestools.enums.SubscriptionTier;
import com.winworld.coursestools.enums.TradingViewDeliveryStatus;
import com.winworld.coursestools.exception.exceptions.DataValidationException;
import com.winworld.coursestools.exception.exceptions.TradingViewUserNotFoundException;
import com.winworld.coursestools.dto.external.ActivateTradingViewAccessDto;
import org.mockito.ArgumentCaptor;
import java.util.List;

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
import static com.winworld.coursestools.enums.TradingViewExpirationPolicy.CUSTOMER_PAYMENT_BUFFER;
import static com.winworld.coursestools.enums.TradingViewExpirationPolicy.EXACT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

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
    private TradingViewRetryService tradingViewRetryService;

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

    @BeforeEach
    void setUpActivationCommandSlot() {
        TradingViewRetryJob job = TradingViewRetryJob.builder()
                .id(501)
                .commandId("test-command")
                .build();
        org.mockito.Mockito.lenient()
                .when(tradingViewRetryService.stageActivation(anyInt(), any()))
                .thenReturn("test-command");
        org.mockito.Mockito.lenient()
                .when(tradingViewRetryService.lockCurrentActivation(anyInt(), eq("test-command")))
                .thenReturn(Optional.of(job));
    }

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
        when(subscriptionMapper.toEvent(
                user, SubscriptionEventType.EXTENDED, existingSubscription, CUSTOMER_PAYMENT_BUFFER))
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
        when(subscriptionMapper.toEvent(
                user, SubscriptionEventType.RESTORED, existingSubscription, CUSTOMER_PAYMENT_BUFFER))
                .thenReturn(event);

        LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC).plusDays(30);
        subscriptionService.updateUserSubscriptionAfterPayment(existingSubscription, order, user, Map.of());
        LocalDateTime after = LocalDateTime.now(ZoneOffset.UTC).plusDays(30);

        assertEquals(PaymentMethod.CRYPTO, existingSubscription.getPaymentMethod());
        assertFalse(existingSubscription.getExpiredAt().isBefore(before.minusNanos(1_000)));
        assertFalse(existingSubscription.getExpiredAt().isAfter(after.plusNanos(1_000)));
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
        when(subscriptionMapper.toEvent(
                eq(user), eq(SubscriptionEventType.CREATED), any(UserSubscription.class), eq(CUSTOMER_PAYMENT_BUFFER)))
                .thenReturn(new SubscriptionChangeStatusEvent());

        subscriptionService.updateUserSubscriptionAfterPayment(null, order, user, Map.of());

        UserSubscription created = user.getSubscriptions().get(0);
        LocalDateTime after = LocalDateTime.now(ZoneOffset.UTC).plusDays(30);
        assertFalse(created.getExpiredAt().isBefore(before.minusNanos(1_000)));
        assertFalse(created.getExpiredAt().isAfter(after.plusNanos(1_000)));
    }

    @Test
    void updateUserSubscriptionAfterPayment_activeNonStripeRenewalExtendsFromExistingExpiry() {
        User user = new User();
        user.setId(4);

        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setName(Plan.MONTH);
        plan.setDurationDays(30);
        plan.setPrice(new BigDecimal("29.99"));

        LocalDateTime existingExpiration = LocalDateTime.now(ZoneOffset.UTC).plusDays(12);
        UserSubscription existingSubscription = UserSubscription.builder()
                .id(12)
                .user(user)
                .plan(plan)
                .status(SubscriptionStatus.GRANTED)
                .paymentMethod(PaymentMethod.CRYPTO)
                .price(new BigDecimal("29.99"))
                .isTrial(false)
                .expiredAt(existingExpiration)
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

        when(subscriptionMapper.toEvent(
                user, SubscriptionEventType.EXTENDED, existingSubscription, CUSTOMER_PAYMENT_BUFFER))
                .thenReturn(new SubscriptionChangeStatusEvent());

        subscriptionService.updateUserSubscriptionAfterPayment(existingSubscription, order, user, Map.of());

        assertEquals(existingExpiration.plusDays(30), existingSubscription.getExpiredAt());
    }

    @Test
    void updateUserSubscriptionAfterPayment_expiredTrialStartsPaidDurationFromNow() {
        User user = userWithTradingViewName("expired-trial");
        user.setSubscriptions(new ArrayList<>());
        SubscriptionPlan plan = monthlyPlan();
        UserSubscription trial = UserSubscription.builder()
                .id(20)
                .user(user)
                .plan(plan)
                .status(SubscriptionStatus.GRACE_PERIOD)
                .paymentMethod(PaymentMethod.MANUAL)
                .isTrial(true)
                .expiredAt(LocalDateTime.now(ZoneOffset.UTC).minusDays(34))
                .build();
        user.getSubscriptions().add(trial);
        Order order = paidOrder(user, plan, PaymentMethod.CRYPTO);
        when(userSubscriptionService.save(any(UserSubscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(subscriptionMapper.toEvent(
                eq(user), eq(SubscriptionEventType.CREATED), any(UserSubscription.class),
                eq(CUSTOMER_PAYMENT_BUFFER)))
                .thenReturn(new SubscriptionChangeStatusEvent());

        LocalDateTime lowerBound = LocalDateTime.now(ZoneOffset.UTC).plusDays(30);
        subscriptionService.updateUserSubscriptionAfterPayment(trial, order, user, Map.of());
        LocalDateTime upperBound = LocalDateTime.now(ZoneOffset.UTC).plusDays(30);

        UserSubscription paid = user.getSubscriptions().get(1);
        assertFalse(paid.getExpiredAt().isBefore(lowerBound.minusNanos(1_000)));
        assertFalse(paid.getExpiredAt().isAfter(upperBound.plusNanos(1_000)));
        assertEquals(SubscriptionStatus.TERMINATED, trial.getStatus());
    }

    @Test
    void updateUserSubscriptionAfterPayment_activeTrialKeepsRemainingAccess() {
        User user = userWithTradingViewName("active-trial");
        user.setSubscriptions(new ArrayList<>());
        SubscriptionPlan plan = monthlyPlan();
        LocalDateTime trialExpiration = LocalDateTime.now(ZoneOffset.UTC).plusDays(4);
        UserSubscription trial = UserSubscription.builder()
                .id(21)
                .user(user)
                .plan(plan)
                .status(SubscriptionStatus.GRANTED)
                .paymentMethod(PaymentMethod.MANUAL)
                .isTrial(true)
                .expiredAt(trialExpiration)
                .build();
        user.getSubscriptions().add(trial);
        Order order = paidOrder(user, plan, PaymentMethod.CRYPTO);
        when(userSubscriptionService.save(any(UserSubscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(subscriptionMapper.toEvent(
                eq(user), eq(SubscriptionEventType.CREATED), any(UserSubscription.class),
                eq(CUSTOMER_PAYMENT_BUFFER)))
                .thenReturn(new SubscriptionChangeStatusEvent());

        subscriptionService.updateUserSubscriptionAfterPayment(trial, order, user, Map.of());

        assertEquals(trialExpiration.plusDays(30), user.getSubscriptions().get(1).getExpiredAt());
    }

    @Test
    void updateUserSubscriptionAfterPayment_expiredPaidRowStartsRenewalFromNow() {
        User user = userWithTradingViewName("expired-paid");
        SubscriptionPlan plan = monthlyPlan();
        UserSubscription expired = UserSubscription.builder()
                .id(22)
                .user(user)
                .plan(plan)
                .status(SubscriptionStatus.GRANTED)
                .paymentMethod(PaymentMethod.CRYPTO)
                .isTrial(false)
                .expiredAt(LocalDateTime.now(ZoneOffset.UTC).minusDays(2))
                .build();
        Order order = paidOrder(user, plan, PaymentMethod.CRYPTO);
        when(subscriptionMapper.toEvent(
                user, SubscriptionEventType.EXTENDED, expired, CUSTOMER_PAYMENT_BUFFER))
                .thenReturn(new SubscriptionChangeStatusEvent());

        LocalDateTime lowerBound = LocalDateTime.now(ZoneOffset.UTC).plusDays(30);
        subscriptionService.updateUserSubscriptionAfterPayment(expired, order, user, Map.of());
        LocalDateTime upperBound = LocalDateTime.now(ZoneOffset.UTC).plusDays(30);

        assertFalse(expired.getExpiredAt().isBefore(lowerBound.minusNanos(1_000)));
        assertFalse(expired.getExpiredAt().isAfter(upperBound.plusNanos(1_000)));
    }

    @ParameterizedTest
    @EnumSource(value = Plan.class, names = {"MONTH", "YEAR"})
    void adminClassicPaidPlansPublishExactNonPaymentPolicy(Plan planName) {
        User user = userWithTradingViewName("manual-month");
        SubscriptionPlan plan = monthlyPlan();
        plan.setName(planName);
        SubscriptionType type = new SubscriptionType();
        type.setId(30);
        type.setName(SubscriptionName.COURSESTOOLS);
        type.setPlans(List.of(plan));
        plan.setSubscriptionType(type);
        UserSubscription current = UserSubscription.builder()
                .id(23)
                .user(user)
                .plan(plan)
                .status(SubscriptionStatus.GRANTED)
                .paymentMethod(PaymentMethod.MANUAL)
                .price(plan.getPrice())
                .isTrial(false)
                .expiredAt(LocalDateTime.now(ZoneOffset.UTC).plusDays(5))
                .build();
        when(subscriptionTypeRepository.findByName(SubscriptionName.COURSESTOOLS))
                .thenReturn(Optional.of(type));
        when(userSubscriptionService.getCurrentUserSubBySubTypeId(user.getId(), type.getId()))
                .thenReturn(Optional.of(current));
        when(subscriptionMapper.toEvent(user, SubscriptionEventType.EXTENDED, current, EXACT))
                .thenReturn(new SubscriptionChangeStatusEvent());

        subscriptionService.adminGrantPaid(user, SubscriptionTier.PRO, planName, false);

        verify(subscriptionMapper).toEvent(user, SubscriptionEventType.EXTENDED, current, EXACT);
    }

    @Test
    void adminClassicTrialPublishesExactNonPaymentPolicy() {
        User user = userWithTradingViewName("manual-trial");
        user.setSubscriptions(new ArrayList<>());
        SubscriptionPlan trialPlan = monthlyPlan();
        SubscriptionType type = new SubscriptionType();
        type.setId(33);
        type.setName(SubscriptionName.COURSESTOOLS);
        type.setPlans(List.of(trialPlan));
        trialPlan.setSubscriptionType(type);
        when(subscriptionTypeRepository.findByName(SubscriptionName.COURSESTOOLS))
                .thenReturn(Optional.of(type));
        when(userSubscriptionService.getCurrentUserSubBySubTypeId(user.getId(), type.getId()))
                .thenReturn(Optional.empty());
        when(userSubscriptionService.save(any(UserSubscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(subscriptionMapper.toEvent(
                eq(user), eq(SubscriptionEventType.TRIAL_CREATED), any(UserSubscription.class), eq(EXACT)))
                .thenReturn(new SubscriptionChangeStatusEvent());

        subscriptionService.adminGrantTrial(
                user, SubscriptionTier.PRO, java.time.LocalDate.now().plusDays(7));

        verify(subscriptionMapper).toEvent(
                eq(user), eq(SubscriptionEventType.TRIAL_CREATED), any(UserSubscription.class), eq(EXACT));
    }

    @Test
    void adminCustomPublishesExactNonPaymentPolicy() {
        User user = userWithTradingViewName("manual-custom");
        SubscriptionPlan plan = monthlyPlan();
        SubscriptionType type = new SubscriptionType();
        type.setId(34);
        UserSubscription current = UserSubscription.builder()
                .id(24)
                .user(user)
                .plan(plan)
                .status(SubscriptionStatus.GRACE_PERIOD)
                .paymentMethod(PaymentMethod.MANUAL)
                .isTrial(false)
                .expiredAt(LocalDateTime.now(ZoneOffset.UTC).minusDays(1))
                .build();
        when(subscriptionTypeRepository.findByName(SubscriptionName.COURSESTOOLS))
                .thenReturn(Optional.of(type));
        when(userSubscriptionService.getCurrentUserSubBySubTypeId(user.getId(), type.getId()))
                .thenReturn(Optional.of(current));
        when(userSubscriptionService.save(current)).thenReturn(current);
        when(subscriptionMapper.toEvent(user, SubscriptionEventType.EXTENDED, current, EXACT))
                .thenReturn(new SubscriptionChangeStatusEvent());

        subscriptionService.adminCustomUpdateExpiry(user, java.time.LocalDate.now().plusDays(10));

        verify(subscriptionMapper).toEvent(user, SubscriptionEventType.EXTENDED, current, EXACT);
    }

    @Test
    void directExtendSubmitsExactPayloadWithoutChangingSubscription() {
        User user = userWithTradingViewName("AryanSN484");
        SubscriptionPlan plan = monthlyPlan();
        SubscriptionType type = new SubscriptionType();
        type.setId(31);
        type.setPlans(List.of(plan));
        UserSubscription current = UserSubscription.builder()
                .id(4968)
                .user(user)
                .plan(plan)
                .status(SubscriptionStatus.GRACE_PERIOD)
                .paymentMethod(PaymentMethod.CRYPTO)
                .isTrial(false)
                .expiredAt(LocalDateTime.of(2026, 8, 15, 15, 52))
                .build();
        when(subscriptionTypeRepository.findByName(SubscriptionName.COURSESTOOLS))
                .thenReturn(Optional.of(type));
        when(userSubscriptionService.getUserSubBySubTypeIdNotTerminated(user.getId(), type.getId()))
                .thenReturn(Optional.of(current));
        when(activatingSubscriptionService.activateTradingViewAccess(eq(user.getId()), any()))
                .thenReturn(TradingViewDeliveryStatus.DELIVERED);
        ArgumentCaptor<ActivateTradingViewAccessDto> payload =
                ArgumentCaptor.forClass(ActivateTradingViewAccessDto.class);

        var response = subscriptionService.directExtendTradingViewAccess(
                user, java.time.LocalDate.of(2026, 9, 19), 1);

        verify(activatingSubscriptionService).activateTradingViewAccess(eq(user.getId()), payload.capture());
        assertEquals(LocalDateTime.of(2026, 9, 19, 0, 0), payload.getValue().getExpiration());
        assertEquals(SubscriptionTier.PRO, payload.getValue().getTier());
        assertEquals(TradingViewDeliveryStatus.DELIVERED, response.getDeliveryStatus());
        assertEquals(4968, response.getSubscriptionId());
        assertEquals(LocalDateTime.of(2026, 8, 15, 15, 52), current.getExpiredAt());
        assertEquals(SubscriptionStatus.GRACE_PERIOD, current.getStatus());
        verify(userSubscriptionService, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void directExtendRejectsUserWithoutNonTerminatedSubscription() {
        User user = userWithTradingViewName("no-sub");
        SubscriptionType type = new SubscriptionType();
        type.setId(32);
        when(subscriptionTypeRepository.findByName(SubscriptionName.COURSESTOOLS))
                .thenReturn(Optional.of(type));
        when(userSubscriptionService.getUserSubBySubTypeIdNotTerminated(user.getId(), type.getId()))
                .thenReturn(Optional.empty());

        DataValidationException error = assertThrows(DataValidationException.class, () ->
                subscriptionService.directExtendTradingViewAccess(
                        user, java.time.LocalDate.of(2026, 9, 19), 1));

        assertTrue(error.getMessage().contains("requires an existing non-terminated subscription"));
        verify(activatingSubscriptionService, never()).activateTradingViewAccess(any(), any());
    }

    @Test
    void schedulerPaths_paidThenTrialTerminatePendingAndGraceTrials() {
        UserSubscription pendingTrial = expiredTrial(41, 141, SubscriptionStatus.PENDING);
        UserSubscription graceTrial = expiredTrial(42, 142, SubscriptionStatus.GRACE_PERIOD);
        when(userSubscriptionService.findAllExpiredSubscriptionsByStatus(SubscriptionStatus.GRANTED))
                .thenReturn(List.of());
        when(userSubscriptionService.findAllWithExpiredTrialSubscription())
                .thenReturn(List.of(pendingTrial, graceTrial));
        when(subscriptionMapper.toEvent(
                pendingTrial.getUser(), SubscriptionEventType.TRIAL_ENDED, pendingTrial, EXACT))
                .thenReturn(new SubscriptionChangeStatusEvent());
        when(subscriptionMapper.toEvent(
                graceTrial.getUser(), SubscriptionEventType.TRIAL_ENDED, graceTrial, EXACT))
                .thenReturn(new SubscriptionChangeStatusEvent());

        subscriptionService.deactivateExpiredSubscriptions();
        List<Integer> userIds = subscriptionService.deactivateExpiredTrialSubscriptions();

        assertEquals(SubscriptionStatus.TERMINATED, pendingTrial.getStatus());
        assertEquals(SubscriptionStatus.TERMINATED, graceTrial.getStatus());
        assertEquals(List.of(141, 142), userIds);
    }

    @Test
    void schedulerPaths_trialThenPaidCannotMoveExpiredTrialToGrace() {
        UserSubscription trial = expiredTrial(43, 143, SubscriptionStatus.GRANTED);
        when(userSubscriptionService.findAllWithExpiredTrialSubscription()).thenReturn(List.of(trial));
        when(userSubscriptionService.findAllExpiredSubscriptionsByStatus(SubscriptionStatus.GRANTED))
                .thenReturn(List.of());
        when(subscriptionMapper.toEvent(trial.getUser(), SubscriptionEventType.TRIAL_ENDED, trial, EXACT))
                .thenReturn(new SubscriptionChangeStatusEvent());

        subscriptionService.deactivateExpiredTrialSubscriptions();
        subscriptionService.deactivateExpiredSubscriptions();

        assertEquals(SubscriptionStatus.TERMINATED, trial.getStatus());
        verify(subscriptionDeactivationService, never()).deactivateSingleSubscription(trial.getId());
    }

    @Test
    void syncStripeSubscriptionUpdated_updatesPeriodEndAndStripeMetadata() {
        long currentPeriodEnd = 1777698505L;
        User user = new User();
        user.setId(4);
        UserSubscription subscription = UserSubscription.builder()
                .id(13)
                .user(user)
                .status(SubscriptionStatus.GRANTED)
                .paymentMethod(PaymentMethod.STRIPE)
                .isTrial(false)
                .expiredAt(LocalDateTime.of(2026, 5, 2, 0, 0))
                .paymentProviderData(new HashMap<>(Map.of(StripePaymentService.SUBSCRIPTION_ID, "sub_sync")))
                .build();

        SubscriptionChangeStatusEvent event = new SubscriptionChangeStatusEvent();
        when(userSubscriptionRepository.findByStripeSubscriptionId("sub_sync")).thenReturn(Optional.of(subscription));
        when(subscriptionMapper.toEvent(user, SubscriptionEventType.EXTENDED, subscription, EXACT)).thenReturn(event);

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
        verify(eventPublisher).publishEvent(event);
    }

    @Test
    void syncStripeSubscriptionUpdated_doesNotRepublishTradingViewEventWhenPeriodEndUnchanged() {
        long currentPeriodEnd = 1777698505L;
        User user = new User();
        user.setId(7);
        UserSubscription subscription = UserSubscription.builder()
                .id(16)
                .user(user)
                .status(SubscriptionStatus.GRANTED)
                .paymentMethod(PaymentMethod.STRIPE)
                .isTrial(false)
                .expiredAt(LocalDateTime.ofInstant(Instant.ofEpochSecond(currentPeriodEnd), ZoneOffset.UTC))
                .paymentProviderData(new HashMap<>(Map.of(StripePaymentService.SUBSCRIPTION_ID, "sub_same")))
                .build();

        when(userSubscriptionRepository.findByStripeSubscriptionId("sub_same")).thenReturn(Optional.of(subscription));

        subscriptionService.syncStripeSubscriptionUpdated(StripeSubscriptionLifecycleDto.builder()
                .subscriptionId("sub_same")
                .currentPeriodEnd(currentPeriodEnd)
                .status("active")
                .cancelAtPeriodEnd(true)
                .build());

        assertEquals(true, subscription.getPaymentProviderData().get(CANCEL_AT_PERIOD_END));
        verify(userSubscriptionService).save(subscription);
        verify(eventPublisher, never()).publishEvent(any());
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
        when(subscriptionMapper.toEvent(user, SubscriptionEventType.GRACE_PERIOD_END, subscription, EXACT))
                .thenReturn(event);

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

    private SubscriptionType lifetimeSubscriptionType(SubscriptionTier tier) {
        SubscriptionPlan lifetimePlan = new SubscriptionPlan();
        lifetimePlan.setTier(tier);
        lifetimePlan.setName(Plan.LIFETIME);
        SubscriptionType type = new SubscriptionType();
        type.setName(SubscriptionName.COURSESTOOLS);
        type.setPlans(List.of(lifetimePlan));
        return type;
    }

    private SubscriptionPlan monthlyPlan() {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setName(Plan.MONTH);
        plan.setTier(SubscriptionTier.PRO);
        plan.setDurationDays(30);
        plan.setPrice(new BigDecimal("14.90"));
        return plan;
    }

    private Order paidOrder(User user, SubscriptionPlan plan, PaymentMethod paymentMethod) {
        return Order.builder()
                .id(997)
                .user(user)
                .plan(plan)
                .originalPrice(plan.getPrice())
                .totalPrice(plan.getPrice())
                .paymentMethod(paymentMethod)
                .orderType(OrderType.ONE_TIME)
                .status(OrderStatus.PAID)
                .build();
    }

    private UserSubscription expiredTrial(int subscriptionId, int userId, SubscriptionStatus status) {
        User user = new User();
        user.setId(userId);
        return UserSubscription.builder()
                .id(subscriptionId)
                .user(user)
                .plan(monthlyPlan())
                .status(status)
                .paymentMethod(PaymentMethod.MANUAL)
                .isTrial(true)
                .expiredAt(LocalDateTime.now(ZoneOffset.UTC).minusDays(1))
                .build();
    }

    private User userWithTradingViewName(String tvName) {
        User user = new User();
        user.setId(1);
        user.setEmail("e@x.com");
        UserSocial social = new UserSocial();
        social.setTradingViewName(tvName);
        user.setSocial(social);
        return user;
    }

    @Test
    void grantLifetimeToExisting_activatesTvBeforeCancelingStripe() {
        when(subscriptionTypeRepository.findByName(SubscriptionName.COURSESTOOLS))
                .thenReturn(Optional.of(lifetimeSubscriptionType(SubscriptionTier.PRO)));
        User user = userWithTradingViewName("nick");
        UserSubscription subscription = UserSubscription.builder()
                .paymentMethod(PaymentMethod.STRIPE)
                .build();

        subscriptionService.grantLifetimeToExistingSubscription(subscription, user, SubscriptionTier.PRO);

        // TV activation must happen BEFORE the irreversible Stripe cancel, so a 404 aborts cleanly.
        InOrder inOrder = inOrder(activatingSubscriptionService, stripePaymentService);
        inOrder.verify(activatingSubscriptionService).activateTradingViewAccess(eq(1), any());
        inOrder.verify(stripePaymentService).cancelSubscription(subscription);
        assertEquals(SubscriptionStatus.GRANTED, subscription.getStatus());
    }

    @Test
    void grantLifetimeToExisting_doesNotCancelStripe_whenTvNicknameNotFound() {
        when(subscriptionTypeRepository.findByName(SubscriptionName.COURSESTOOLS))
                .thenReturn(Optional.of(lifetimeSubscriptionType(SubscriptionTier.PRO)));
        User user = userWithTradingViewName("nick");
        UserSubscription subscription = UserSubscription.builder()
                .paymentMethod(PaymentMethod.STRIPE)
                .status(SubscriptionStatus.GRACE_PERIOD)
                .build();
        doThrow(new TradingViewUserNotFoundException("nick"))
                .when(activatingSubscriptionService).activateTradingViewAccess(any(), any());

        assertThrows(TradingViewUserNotFoundException.class, () ->
                subscriptionService.grantLifetimeToExistingSubscription(subscription, user, SubscriptionTier.PRO));

        // The permanent 404 must abort before the irreversible remote Stripe cancel.
        verify(stripePaymentService, never()).cancelSubscription(any());
        assertNotEquals(SubscriptionStatus.GRANTED, subscription.getStatus());
    }
}
