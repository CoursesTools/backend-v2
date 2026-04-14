package com.winworld.coursestools.service;

import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.entity.user.UserSubscription;
import com.winworld.coursestools.enums.SubscriptionStatus;
import com.winworld.coursestools.repository.user.UserSubscriptionRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionStateReconciliationServiceTest {

    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;

    @Mock
    private SubscriptionDeactivationService subscriptionDeactivationService;

    private SubscriptionStateReconciliationService subscriptionStateReconciliationService;

    @BeforeEach
    void setUp() {
        subscriptionStateReconciliationService = new SubscriptionStateReconciliationService(
                userSubscriptionRepository,
                subscriptionDeactivationService,
                new SimpleMeterRegistry()
        );
    }

    @Test
    void discardPastGracePeriodSubscription_terminatesStaleSubscriptionAndReturnsEmpty() {
        User user = new User();
        user.setId(33);

        UserSubscription userSubscription = UserSubscription.builder()
                .id(10)
                .user(user)
                .status(SubscriptionStatus.GRACE_PERIOD)
                .isTrial(false)
                .expiredAt(LocalDateTime.now().minusDays(SubscriptionService.GRACE_PERIOD_DAYS + 1L))
                .build();

        Optional<UserSubscription> result = subscriptionStateReconciliationService
                .discardPastGracePeriodSubscription(userSubscription);

        assertTrue(result.isEmpty());
        verify(subscriptionDeactivationService).terminatePastGracePeriodSubscription(10);
    }

    @Test
    void discardPastGracePeriodSubscription_keepsUsableSubscription() {
        UserSubscription userSubscription = UserSubscription.builder()
                .id(11)
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
    void reconcilePastGracePeriodSubscriptions_terminatesEveryStaleSubscription() {
        UserSubscription first = UserSubscription.builder().id(1).build();
        UserSubscription second = UserSubscription.builder().id(2).build();
        when(userSubscriptionRepository.findAllNonTerminatedPastGracePeriod(any(LocalDateTime.class)))
                .thenReturn(List.of(first, second));
        when(userSubscriptionRepository.countAllNonTerminatedPastGracePeriod(any(LocalDateTime.class))).thenReturn(0L);

        int terminated = subscriptionStateReconciliationService.reconcilePastGracePeriodSubscriptions("test");

        assertEquals(2, terminated);
        verify(subscriptionDeactivationService).terminatePastGracePeriodSubscription(1);
        verify(subscriptionDeactivationService).terminatePastGracePeriodSubscription(2);
    }
}
