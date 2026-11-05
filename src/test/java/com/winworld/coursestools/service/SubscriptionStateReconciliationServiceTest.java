package com.winworld.coursestools.service;

import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.entity.user.UserSubscription;
import com.winworld.coursestools.entity.subscription.SubscriptionPlan;
import com.winworld.coursestools.enums.Plan;
import com.winworld.coursestools.enums.SubscriptionStatus;
import com.winworld.coursestools.repository.user.UserSubscriptionRepository;
import com.winworld.coursestools.service.ops.SystemSettingsService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionStateReconciliationServiceTest {

    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;

    @Mock
    private SubscriptionDeactivationService subscriptionDeactivationService;

    @Mock
    private SystemSettingsService systemSettingsService;

    private SubscriptionStateReconciliationService subscriptionStateReconciliationService;

    @BeforeEach
    void setUp() {
        lenient().when(systemSettingsService.getGracePeriodDays()).thenReturn(7);
        subscriptionStateReconciliationService = new SubscriptionStateReconciliationService(
                userSubscriptionRepository,
                subscriptionDeactivationService,
                systemSettingsService,
                new SimpleMeterRegistry()
        );
    }

    @Test
    void isPastGracePeriod_usesTheAdminConfiguredGraceLength() {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setName(Plan.MONTH);
        UserSubscription expiredFourDaysAgo = UserSubscription.builder()
                .id(13)
                .plan(plan)
                .status(SubscriptionStatus.GRACE_PERIOD)
                .isTrial(false)
                .expiredAt(LocalDateTime.now(ZoneOffset.UTC).minusDays(4))
                .build();

        assertFalse(subscriptionStateReconciliationService.isPastGracePeriod(expiredFourDaysAgo));

        when(systemSettingsService.getGracePeriodDays()).thenReturn(3);

        assertTrue(subscriptionStateReconciliationService.isPastGracePeriod(expiredFourDaysAgo));
    }

    @Test
    void discardPastGracePeriodSubscription_terminatesStaleSubscriptionAndReturnsEmpty() {
        User user = new User();
        user.setId(33);
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setName(Plan.MONTH);

        UserSubscription userSubscription = UserSubscription.builder()
                .id(10)
                .user(user)
                .plan(plan)
                .status(SubscriptionStatus.GRACE_PERIOD)
                .isTrial(false)
                .expiredAt(LocalDateTime.now().minusDays(SystemSettingsService.DEFAULT_GRACE_PERIOD_DAYS + 1L))
                .build();

        Optional<UserSubscription> result = subscriptionStateReconciliationService
                .discardPastGracePeriodSubscription(userSubscription);

        assertTrue(result.isEmpty());
        verify(subscriptionDeactivationService).terminatePastGracePeriodSubscription(10);
    }

    @Test
    void discardPastGracePeriodSubscription_keepsUsableSubscription() {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setName(Plan.MONTH);

        UserSubscription userSubscription = UserSubscription.builder()
                .id(11)
                .plan(plan)
                .status(SubscriptionStatus.GRANTED)
                .isTrial(false)
                .expiredAt(LocalDateTime.now().minusDays(1))
                .build();

        Optional<UserSubscription> result = subscriptionStateReconciliationService
                .discardPastGracePeriodSubscription(userSubscription);

        assertTrue(result.isPresent());
        assertEquals(userSubscription, result.get());
        verify(subscriptionDeactivationService, never()).terminatePastGracePeriodSubscription(anyInt());
    }

    @Test
    void discardPastGracePeriodSubscription_keepsLifetimeSubscriptionEvenIfExpiredAtLooksOld() {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setName(Plan.LIFETIME);

        UserSubscription userSubscription = UserSubscription.builder()
                .id(12)
                .plan(plan)
                .status(SubscriptionStatus.GRACE_PERIOD)
                .isTrial(false)
                .expiredAt(LocalDateTime.now().minusYears(2))
                .build();

        Optional<UserSubscription> result = subscriptionStateReconciliationService
                .discardPastGracePeriodSubscription(userSubscription);

        assertTrue(result.isPresent());
        assertEquals(userSubscription, result.get());
        verify(subscriptionDeactivationService, never()).terminatePastGracePeriodSubscription(anyInt());
    }

    @Test
    void reconcilePastGracePeriodSubscriptions_terminatesEveryStaleSubscriptionAndReturnsTheirUsers() {
        UserSubscription first = UserSubscription.builder().id(1).build();
        UserSubscription second = UserSubscription.builder().id(2).build();
        when(userSubscriptionRepository.findAllNonTerminatedPastGracePeriod(any(LocalDateTime.class), any()))
                .thenReturn(List.of(first, second));
        when(userSubscriptionRepository.countAllNonTerminatedPastGracePeriod(any(LocalDateTime.class), any())).thenReturn(0L);
        when(subscriptionDeactivationService.terminatePastGracePeriodSubscription(1)).thenReturn(Optional.of(101));
        when(subscriptionDeactivationService.terminatePastGracePeriodSubscription(2)).thenReturn(Optional.of(102));

        List<Integer> terminatedUsers = subscriptionStateReconciliationService.reconcilePastGracePeriodSubscriptions("test");

        assertEquals(List.of(101, 102), terminatedUsers);
        verify(subscriptionDeactivationService).terminatePastGracePeriodSubscription(1);
        verify(subscriptionDeactivationService).terminatePastGracePeriodSubscription(2);
    }
}
