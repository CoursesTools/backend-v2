package com.winworld.coursestools.service;

import com.winworld.coursestools.dto.subscription.SubscriptionReadDto;
import com.winworld.coursestools.dto.user.UserSubscriptionReadDto;
import com.winworld.coursestools.entity.Order;
import com.winworld.coursestools.entity.subscription.SubscriptionPlan;
import com.winworld.coursestools.entity.subscription.SubscriptionType;
import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.entity.user.UserSubscription;
import com.winworld.coursestools.enums.PaymentMethod;
import com.winworld.coursestools.enums.Plan;
import com.winworld.coursestools.enums.SubscriptionEventType;
import com.winworld.coursestools.enums.SubscriptionName;
import com.winworld.coursestools.enums.SubscriptionStatus;
import com.winworld.coursestools.event.SubscriptionChangeStatusEvent;
import com.winworld.coursestools.exception.exceptions.ConflictException;
import com.winworld.coursestools.exception.exceptions.EntityNotFoundException;
import com.winworld.coursestools.mapper.SubscriptionMapperImpl;
import com.winworld.coursestools.mapper.UserMapperImpl;
import com.winworld.coursestools.repository.subscription.SubscriptionPlanRepository;
import com.winworld.coursestools.repository.subscription.SubscriptionTypeRepository;
import com.winworld.coursestools.service.payment.impl.StripePaymentService;
import com.winworld.coursestools.service.user.UserDataService;
import com.winworld.coursestools.service.user.UserSubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    private UserDataService userDataService;
    @Mock
    private SubscriptionTypeRepository subscriptionTypeRepository;
    @Mock
    private SubscriptionPlanRepository subscriptionPlanRepository;
    @Mock
    private UserSubscriptionService userSubscriptionService;
    @Mock
    private StripePaymentService stripePaymentService;
    @Spy
    private SubscriptionMapperImpl subscriptionMapper;
    @Spy
    private UserMapperImpl userMapper;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Spy
    @InjectMocks
    private SubscriptionService subscriptionService;

    private final int trialDays = 3;

    private User user;
    private SubscriptionType type;
    private SubscriptionPlan plan;
    private UserSubscription userSubscription;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(subscriptionService, "ctProTrialDays", trialDays);
        user = User.builder()
                .id(1)
                .subscriptions(new ArrayList<>())
                .build();
        plan = SubscriptionPlan.builder()
                .id(1)
                .name(Plan.MONTH)
                .durationDays(30)
                .price(BigDecimal.ONE)
                .build();
        type = SubscriptionType.builder()
                .id(1)
                .plans(List.of(plan))
                .name(SubscriptionName.COURSESTOOLSPRO)
                .build();
        userSubscription = UserSubscription.builder()
                .id(1)
                .user(user)
                .build();
    }

    @Test
    void getSubscription_shouldReturnDto_whenTypeExists() {
        SubscriptionReadDto dto = new SubscriptionReadDto();
        when(subscriptionTypeRepository.findByName(SubscriptionName.COURSESTOOLSPRO))
                .thenReturn(Optional.of(type));
        when(subscriptionMapper.toDto(type)).thenReturn(dto);

        SubscriptionReadDto result = subscriptionService.getSubscription(SubscriptionName.COURSESTOOLSPRO);

        assertSame(dto, result);
    }

    @Test
    void getSubscription_shouldThrow_whenNotFound() {
        when(subscriptionTypeRepository.findByName(any())).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () ->
                subscriptionService.getSubscription(SubscriptionName.COURSESTOOLSPRO));
    }

    @Test
    void getSubscriptionPlan_shouldReturnDto_whenPlanExists() {
        SubscriptionPlan plan = new SubscriptionPlan();
        when(subscriptionPlanRepository.findById(anyInt()))
                .thenReturn(Optional.of(plan));

        SubscriptionPlan result = subscriptionService.getSubscriptionPlan(1);

        assertSame(plan, result);
    }

    @Test
    void getSubscriptionPlan_shouldThrow_whenNotFound() {
        when(subscriptionPlanRepository.findById(anyInt())).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () ->
                subscriptionService.getSubscriptionPlan(1));
    }

    @Test
    void getSubscriptionTypeByName_shouldReturnType_whenExists() {
        when(subscriptionTypeRepository.findByName(SubscriptionName.COURSESTOOLSPRO))
                .thenReturn(Optional.of(type));

        SubscriptionType result = subscriptionService.getSubscriptionTypeByName(SubscriptionName.COURSESTOOLSPRO);

        assertSame(type, result);
    }

    @Test
    void getSubscriptionTypeByName_shouldThrow_whenNotFound() {
        when(subscriptionTypeRepository.findByName(any())).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () ->
                subscriptionService.getSubscriptionTypeByName(SubscriptionName.COURSESTOOLSPRO));
    }

    @Test
    void activateCtProTrialForUser_shouldThrow_whenUserAlreadyUseTrial() {
        when(userDataService.getUserById(anyInt())).thenReturn(user);
        when(subscriptionTypeRepository.findByName(SubscriptionName.COURSESTOOLSPRO))
                .thenReturn(Optional.of(type));
        when(userSubscriptionService.hasEverHadSubscriptionOfType(type.getId(), user.getId()))
                .thenReturn(true);

        assertThrows(ConflictException.class, () ->
                subscriptionService.activateCtProTrialForUser(1));
    }

    @Test
    void activateCtProTrialForUser_shouldActivateTrial() {
        when(userDataService.getUserById(anyInt())).thenReturn(user);
        when(subscriptionTypeRepository.findByName(SubscriptionName.COURSESTOOLSPRO))
                .thenReturn(Optional.of(type));
        when(userSubscriptionService.hasEverHadSubscriptionOfType(type.getId(), user.getId()))
                .thenReturn(false);
        when(userSubscriptionService.save(any(UserSubscription.class))).thenReturn(userSubscription);

        var dto = subscriptionService.activateCtProTrialForUser(1);

        var captor = ArgumentCaptor.forClass(SubscriptionChangeStatusEvent.class);

        verify(userSubscriptionService).save(any(UserSubscription.class));
        verify(eventPublisher).publishEvent(captor.capture());

        assertEquals(SubscriptionEventType.TRIAL_CREATED, captor.getValue().getEventType());
        assertEquals(1, captor.getValue().getUserSubscriptionId());
        assertEquals(1, dto.getId());
    }

    @Test
    void deactivateExpiredSubscriptions_shouldDeactivateAndEnterGracePeriod() {
        // Arrange
        List<UserSubscription> expiredSubscriptions = List.of(
            UserSubscription.builder()
                .id(1)
                .user(user)
                .status(SubscriptionStatus.GRANTED)
                .paymentMethod(PaymentMethod.STRIPE)
                .build()
        );
        when(userSubscriptionService.findAllExpiredSubscriptionsByStatus(
                SubscriptionStatus.GRANTED))
                .thenReturn(expiredSubscriptions);

        // Act
        subscriptionService.deactivateExpiredSubscriptions();

        // Assert
        assertEquals(SubscriptionStatus.GRACE_PERIOD,
                expiredSubscriptions.get(0).getStatus());
        verify(stripePaymentService).cancelSubscription(expiredSubscriptions.get(0));

        ArgumentCaptor<SubscriptionChangeStatusEvent> eventCaptor =
                ArgumentCaptor.forClass(SubscriptionChangeStatusEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        SubscriptionChangeStatusEvent capturedEvent = eventCaptor.getValue();
        assertEquals(SubscriptionEventType.GRACE_PERIOD_START, capturedEvent.getEventType());
        assertEquals(1, capturedEvent.getUserSubscriptionId());
    }

    @Test
    void deactivateExpiredTrialSubscriptions_shouldTerminateTrialSubscriptions() {
        // Arrange
        List<UserSubscription> expiredTrialSubscriptions = List.of(
            UserSubscription.builder()
                .id(1)
                .user(user)
                .isTrial(true)
                .build()
        );
        when(userSubscriptionService.findAllWithExpiredTrialSubscription())
                .thenReturn(expiredTrialSubscriptions);

        // Act
        subscriptionService.deactivateExpiredTrialSubscriptions();

        // Assert
        assertEquals(SubscriptionStatus.TERMINATED,
                expiredTrialSubscriptions.get(0).getStatus());

        ArgumentCaptor<SubscriptionChangeStatusEvent> eventCaptor =
                ArgumentCaptor.forClass(SubscriptionChangeStatusEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        SubscriptionChangeStatusEvent capturedEvent = eventCaptor.getValue();
        assertEquals(SubscriptionEventType.TRIAL_ENDED, capturedEvent.getEventType());
        assertEquals(1, capturedEvent.getUserSubscriptionId());
    }

    @Test
    void deactivateExpiredGracePeriodSubscriptions_shouldTerminateGracePeriodSubscriptions() {
        // Arrange
        List<UserSubscription> expiredGracePeriodSubscriptions = List.of(
            UserSubscription.builder()
                .id(1)
                .user(user)
                .status(SubscriptionStatus.GRACE_PERIOD)
                .build()
        );
        when(userSubscriptionService.findAllExpiredSubscriptionsByStatus(
                SubscriptionStatus.GRACE_PERIOD))
                .thenReturn(expiredGracePeriodSubscriptions);

        // Act
        subscriptionService.deactivateExpiredGracePeriodSubscriptions();

        // Assert
        assertEquals(SubscriptionStatus.TERMINATED,
                expiredGracePeriodSubscriptions.get(0).getStatus());

        ArgumentCaptor<SubscriptionChangeStatusEvent> eventCaptor =
                ArgumentCaptor.forClass(SubscriptionChangeStatusEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        SubscriptionChangeStatusEvent capturedEvent = eventCaptor.getValue();
        assertEquals(SubscriptionEventType.GRACE_PERIOD_END, capturedEvent.getEventType());
        assertEquals(1, capturedEvent.getUserSubscriptionId());
    }

    @Test
    void updateUserSubscriptionAfterPayment_shouldCreateNewSubscription_whenCurrentSubscriptionIsNull() {
        // Arrange
        Order order = Order.builder()
                .plan(plan)
                .paymentMethod(PaymentMethod.STRIPE)
                .build();
        Map<String, Object> paymentData = Map.of("paymentId", "stripe123");
        when(userSubscriptionService.save(any(UserSubscription.class))).thenReturn(userSubscription);

        // Act
        subscriptionService.updateUserSubscriptionAfterPayment(null, order, user, paymentData);

        // Assert
        ArgumentCaptor<UserSubscription> subscriptionCaptor = ArgumentCaptor.forClass(UserSubscription.class);
        verify(userSubscriptionService).save(subscriptionCaptor.capture());

        UserSubscription capturedSubscription = subscriptionCaptor.getValue();
        assertEquals(PaymentMethod.STRIPE, capturedSubscription.getPaymentMethod());
        assertEquals(plan, capturedSubscription.getPlan());
        assertEquals(SubscriptionStatus.PENDING, capturedSubscription.getStatus());
        assertEquals(false, capturedSubscription.getIsTrial());
        assertEquals(paymentData, capturedSubscription.getPaymentProviderData());

        ArgumentCaptor<SubscriptionChangeStatusEvent> eventCaptor =
                ArgumentCaptor.forClass(SubscriptionChangeStatusEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(SubscriptionEventType.CREATED, eventCaptor.getValue().getEventType());
    }

    @Test
    void updateUserSubscriptionAfterPayment_shouldCreateNewSubscription_whenCurrentSubscriptionIsTrial() {
        // Arrange
        UserSubscription trialSubscription = UserSubscription.builder()
                .id(2)
                .user(user)
                .isTrial(true)
                .expiredAt(LocalDateTime.now().plusDays(1))
                .status(SubscriptionStatus.GRANTED)
                .build();

        Order order = Order.builder()
                .plan(plan)
                .paymentMethod(PaymentMethod.STRIPE)
                .build();
        Map<String, Object> paymentData = Map.of("paymentId", "stripe123");
        when(userSubscriptionService.save(any(UserSubscription.class))).thenReturn(userSubscription);

        // Act
        subscriptionService.updateUserSubscriptionAfterPayment(trialSubscription, order, user, paymentData);

        // Assert
        ArgumentCaptor<UserSubscription> subscriptionCaptor = ArgumentCaptor.forClass(UserSubscription.class);
        verify(userSubscriptionService).save(subscriptionCaptor.capture());

        UserSubscription capturedSubscription = subscriptionCaptor.getValue();
        assertEquals(PaymentMethod.STRIPE, capturedSubscription.getPaymentMethod());
        assertEquals(plan, capturedSubscription.getPlan());
        assertEquals(SubscriptionStatus.PENDING, capturedSubscription.getStatus());
        assertEquals(false, capturedSubscription.getIsTrial());

        assertEquals(SubscriptionStatus.TERMINATED, trialSubscription.getStatus());

        ArgumentCaptor<SubscriptionChangeStatusEvent> eventCaptor =
                ArgumentCaptor.forClass(SubscriptionChangeStatusEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(SubscriptionEventType.CREATED, eventCaptor.getValue().getEventType());
    }

    @Test
    void updateUserSubscriptionAfterPayment_shouldUpdateGracePeriodSubscription() {
        // Arrange
        UserSubscription gracePeriodSubscription = UserSubscription.builder()
                .id(3)
                .user(user)
                .isTrial(false)
                .status(SubscriptionStatus.GRACE_PERIOD)
                .paymentMethod(PaymentMethod.CRYPTO)
                .build();

        Order order = Order.builder()
                .plan(plan)
                .paymentMethod(PaymentMethod.STRIPE)
                .build();
        Map<String, Object> paymentData = Map.of("paymentId", "stripe123");

        // Act
        subscriptionService.updateUserSubscriptionAfterPayment(gracePeriodSubscription, order, user, paymentData);

        // Assert
        assertEquals(SubscriptionStatus.PENDING, gracePeriodSubscription.getStatus());
        assertEquals(PaymentMethod.STRIPE, gracePeriodSubscription.getPaymentMethod());
        assertEquals(paymentData, gracePeriodSubscription.getPaymentProviderData());
        assertEquals(plan, gracePeriodSubscription.getPlan());

        ArgumentCaptor<SubscriptionChangeStatusEvent> eventCaptor =
                ArgumentCaptor.forClass(SubscriptionChangeStatusEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(SubscriptionEventType.RESTORED, eventCaptor.getValue().getEventType());
    }

    @Test
    void updateUserSubscriptionAfterPayment_shouldExtendExistingSubscription() {
        // Arrange
        UserSubscription existingSubscription = UserSubscription.builder()
                .id(4)
                .user(user)
                .isTrial(false)
                .status(SubscriptionStatus.GRANTED)
                .paymentMethod(PaymentMethod.CRYPTO)
                .expiredAt(LocalDateTime.now().plusDays(5))
                .build();

        Order order = Order.builder()
                .plan(plan)
                .paymentMethod(PaymentMethod.STRIPE)
                .build();
        Map<String, Object> paymentData = Map.of("paymentId", "stripe123");

        // Act
        subscriptionService.updateUserSubscriptionAfterPayment(existingSubscription, order, user, paymentData);

        // Assert
        assertEquals(PaymentMethod.STRIPE, existingSubscription.getPaymentMethod());
        assertEquals(paymentData, existingSubscription.getPaymentProviderData());
        assertEquals(plan, existingSubscription.getPlan());

        ArgumentCaptor<SubscriptionChangeStatusEvent> eventCaptor =
                ArgumentCaptor.forClass(SubscriptionChangeStatusEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(SubscriptionEventType.EXTENDED, eventCaptor.getValue().getEventType());
    }

    @Test
    void updateUserSubscriptionAfterPayment_shouldCancelStripeSubscription_whenChangingFromStripeToOtherPaymentMethod() {
        // Arrange
        UserSubscription stripeSubscription = UserSubscription.builder()
                .id(5)
                .user(user)
                .isTrial(false)
                .status(SubscriptionStatus.GRANTED)
                .paymentMethod(PaymentMethod.STRIPE)
                .expiredAt(LocalDateTime.now().plusDays(5))
                .build();

        Order order = Order.builder()
                .plan(plan)
                .paymentMethod(PaymentMethod.CRYPTO)
                .build();
        Map<String, Object> paymentData = Map.of("receiptId", "receipt123");

        // Act
        subscriptionService.updateUserSubscriptionAfterPayment(stripeSubscription, order, user, paymentData);

        // Assert
        verify(stripePaymentService).cancelSubscription(stripeSubscription);
        assertEquals(PaymentMethod.CRYPTO, stripeSubscription.getPaymentMethod());
        assertEquals(paymentData, stripeSubscription.getPaymentProviderData());

        ArgumentCaptor<SubscriptionChangeStatusEvent> eventCaptor =
                ArgumentCaptor.forClass(SubscriptionChangeStatusEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(SubscriptionEventType.EXTENDED, eventCaptor.getValue().getEventType());
    }
}
