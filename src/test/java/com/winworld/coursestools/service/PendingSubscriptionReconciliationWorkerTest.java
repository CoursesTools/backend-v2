package com.winworld.coursestools.service;

import com.winworld.coursestools.entity.subscription.SubscriptionPlan;
import com.winworld.coursestools.entity.subscription.SubscriptionType;
import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.entity.user.UserSocial;
import com.winworld.coursestools.entity.user.UserSubscription;
import com.winworld.coursestools.enums.PaymentMethod;
import com.winworld.coursestools.enums.Plan;
import com.winworld.coursestools.enums.SubscriptionEventType;
import com.winworld.coursestools.enums.SubscriptionStatus;
import com.winworld.coursestools.enums.SubscriptionTier;
import com.winworld.coursestools.enums.TradingViewExpirationPolicy;
import com.winworld.coursestools.repository.user.UserSubscriptionRepository;
import com.winworld.coursestools.service.external.TradingViewRetryService;
import com.winworld.coursestools.service.user.UserDataService;
import com.winworld.coursestools.service.user.UserSubscriptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PendingSubscriptionReconciliationWorkerTest {
    @Mock
    private UserSubscriptionRepository repository;
    @Mock
    private UserDataService userDataService;
    @Mock
    private UserSubscriptionService userSubscriptionService;
    @Mock
    private TradingViewRetryService tradingViewRetryService;
    @Mock
    private SubscriptionService subscriptionService;

    @Test
    void orphanedTrial_getsFreshSevenDaysAndExactActivation() {
        LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(15);
        UserSubscription subscription = fixture(true, null, cutoff.minusMinutes(1));
        arrangeCurrent(subscription);
        LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC).plusDays(7).minusSeconds(1);

        assertThat(worker().reconcile(subscription.getId(), cutoff, "test")).isTrue();

        LocalDateTime after = LocalDateTime.now(ZoneOffset.UTC).plusDays(7).plusSeconds(1);
        assertThat(subscription.getExpiredAt()).isBetween(before, after);
        verify(userSubscriptionService).save(subscription);
        verify(subscriptionService).publishSubscriptionEvent(
                subscription.getUser(), SubscriptionEventType.EXTENDED, subscription,
                TradingViewExpirationPolicy.EXACT);
    }

    @Test
    void orphanedCustomerPayment_preservesExpiryAndRestagesPaymentBuffer() {
        LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(15);
        UserSubscription subscription = fixture(false, PaymentMethod.CRYPTO, cutoff.minusMinutes(1));
        LocalDateTime originalExpiration = subscription.getExpiredAt();
        arrangeCurrent(subscription);

        assertThat(worker().reconcile(subscription.getId(), cutoff, "test")).isTrue();

        assertThat(subscription.getExpiredAt()).isEqualTo(originalExpiration);
        verify(userSubscriptionService, never()).save(subscription);
        verify(subscriptionService).publishSubscriptionEvent(
                subscription.getUser(), SubscriptionEventType.EXTENDED, subscription,
                TradingViewExpirationPolicy.CUSTOMER_PAYMENT_BUFFER);
    }

    @Test
    void existingRetryOrDeadJob_isNeverOverwritten() {
        LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(15);
        UserSubscription subscription = fixture(true, null, cutoff.minusMinutes(1));
        arrangeCurrent(subscription);
        when(tradingViewRetryService.hasPendingOrDeadActivation(subscription.getUser().getId()))
                .thenReturn(true);

        assertThat(worker().reconcile(subscription.getId(), cutoff, "test")).isFalse();

        verify(subscriptionService, never()).publishSubscriptionEvent(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        verify(userSubscriptionService, never()).save(subscription);
    }

    @Test
    void supersededTrial_isNotRestoredOverNewerSubscription() {
        LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(15);
        UserSubscription subscription = fixture(true, null, cutoff.minusMinutes(1));
        UserSubscription newer = fixture(false, PaymentMethod.STRIPE, cutoff.minusMinutes(1));
        newer.setId(100);
        when(repository.findById(subscription.getId())).thenReturn(Optional.of(subscription));
        when(userDataService.getUserByIdForUpdate(subscription.getUser().getId()))
                .thenReturn(subscription.getUser());
        when(userSubscriptionService.getUserSubByIdForUpdate(subscription.getId()))
                .thenReturn(subscription);
        when(repository.findAllCurrentBySubTypeNotTerminatedWithPlan(
                subscription.getPlan().getSubscriptionType().getId(), subscription.getUser().getId()))
                .thenReturn(java.util.List.of(subscription, newer));

        assertThat(worker().reconcile(subscription.getId(), cutoff, "test")).isFalse();

        verify(tradingViewRetryService, never()).hasPendingOrDeadActivation(
                subscription.getUser().getId());
        verify(subscriptionService, never()).publishSubscriptionEvent(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    private PendingSubscriptionReconciliationWorker worker() {
        PendingSubscriptionReconciliationWorker worker = new PendingSubscriptionReconciliationWorker(
                repository,
                userDataService,
                userSubscriptionService,
                tradingViewRetryService,
                subscriptionService
        );
        ReflectionTestUtils.setField(worker, "trialDays", 7);
        return worker;
    }

    private void arrangeCurrent(UserSubscription subscription) {
        when(repository.findById(subscription.getId())).thenReturn(Optional.of(subscription));
        when(userDataService.getUserByIdForUpdate(subscription.getUser().getId()))
                .thenReturn(subscription.getUser());
        when(userSubscriptionService.getUserSubByIdForUpdate(subscription.getId()))
                .thenReturn(subscription);
        when(repository.findAllCurrentBySubTypeNotTerminatedWithPlan(
                subscription.getPlan().getSubscriptionType().getId(), subscription.getUser().getId()))
                .thenReturn(java.util.List.of(subscription));
    }

    private UserSubscription fixture(boolean trial, PaymentMethod paymentMethod, LocalDateTime updatedAt) {
        User user = User.builder().id(7).email("trial@example.com").build();
        UserSocial social = UserSocial.builder().id(7).tradingViewName("trial-user").user(user).build();
        user.setSocial(social);
        SubscriptionType type = SubscriptionType.builder().id(1).build();
        SubscriptionPlan plan = SubscriptionPlan.builder()
                .id(5)
                .name(Plan.MONTH)
                .tier(SubscriptionTier.PRO)
                .subscriptionType(type)
                .build();
        return UserSubscription.builder()
                .id(99)
                .updatedAt(updatedAt)
                .user(user)
                .plan(plan)
                .status(SubscriptionStatus.PENDING)
                .isTrial(trial)
                .paymentMethod(paymentMethod)
                .expiredAt(updatedAt.plusDays(7))
                .build();
    }
}
