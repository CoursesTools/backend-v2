package com.winworld.coursestools.listener;

import com.winworld.coursestools.dto.external.ActivateTradingViewAccessDto;
import com.winworld.coursestools.entity.subscription.SubscriptionPlan;
import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.entity.user.UserSubscription;
import com.winworld.coursestools.enums.Plan;
import com.winworld.coursestools.enums.SubscriptionEventType;
import com.winworld.coursestools.enums.SubscriptionStatus;
import com.winworld.coursestools.enums.SubscriptionTier;
import com.winworld.coursestools.enums.TradingViewExpirationPolicy;
import com.winworld.coursestools.event.SubscriptionChangeStatusEvent;
import com.winworld.coursestools.service.EmailService;
import com.winworld.coursestools.service.external.ActivatingSubscriptionService;
import com.winworld.coursestools.service.external.TradingViewRetryService;
import com.winworld.coursestools.service.user.UserSubscriptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionChangeStatusListenerTest {
    @Mock
    private EmailService emailService;
    @Mock
    private ActivatingSubscriptionService activatingSubscriptionService;
    @Mock
    private UserSubscriptionService userSubscriptionService;
    @Mock
    private TradingViewRetryService tradingViewRetryService;

    @Test
    void paymentEvent_addsOneDayToBotOnly() {
        LocalDateTime databaseExpiration = LocalDateTime.of(2026, 9, 18, 9, 51);
        var fixture = fixture(databaseExpiration, Plan.MONTH);

        listener().activateUserSubscription(event(
                fixture.subscription(), SubscriptionEventType.CREATED,
                TradingViewExpirationPolicy.CUSTOMER_PAYMENT_BUFFER));

        ArgumentCaptor<ActivateTradingViewAccessDto> payload =
                ArgumentCaptor.forClass(ActivateTradingViewAccessDto.class);
        verify(activatingSubscriptionService).activateTradingViewAccess(eq(7), payload.capture());
        assertThat(payload.getValue().getExpiration()).isEqualTo(databaseExpiration.plusDays(1));
        assertThat(fixture.subscription().getExpiredAt()).isEqualTo(databaseExpiration);
        assertThat(fixture.subscription().getStatus()).isEqualTo(SubscriptionStatus.GRANTED);
    }

    @Test
    void nonPaymentEvent_sendsExactExpiration() {
        LocalDateTime databaseExpiration = LocalDateTime.of(2026, 9, 19, 0, 0);
        var fixture = fixture(databaseExpiration, Plan.MONTH);

        listener().activateUserSubscription(event(
                fixture.subscription(), SubscriptionEventType.EXTENDED,
                TradingViewExpirationPolicy.EXACT));

        ArgumentCaptor<ActivateTradingViewAccessDto> payload =
                ArgumentCaptor.forClass(ActivateTradingViewAccessDto.class);
        verify(activatingSubscriptionService).activateTradingViewAccess(eq(7), payload.capture());
        assertThat(payload.getValue().getExpiration()).isEqualTo(databaseExpiration);
    }

    @Test
    void lifetimePayment_isNeverArithmeticallyBuffered() {
        LocalDateTime lifetimeExpiration = LocalDateTime.of(2100, 12, 31, 23, 59, 59);
        var fixture = fixture(lifetimeExpiration, Plan.LIFETIME);

        listener().activateUserSubscription(event(
                fixture.subscription(), SubscriptionEventType.CREATED,
                TradingViewExpirationPolicy.CUSTOMER_PAYMENT_BUFFER));

        ArgumentCaptor<ActivateTradingViewAccessDto> payload =
                ArgumentCaptor.forClass(ActivateTradingViewAccessDto.class);
        verify(activatingSubscriptionService).activateTradingViewAccess(eq(7), payload.capture());
        assertThat(payload.getValue().getExpiration()).isEqualTo(lifetimeExpiration);
        assertThat(payload.getValue().isLifetime()).isTrue();
    }

    private SubscriptionChangeStatusListener listener() {
        return new SubscriptionChangeStatusListener(
                List.of(), emailService, activatingSubscriptionService,
                userSubscriptionService, tradingViewRetryService);
    }

    private Fixture fixture(LocalDateTime expiration, Plan planName) {
        User user = new User();
        user.setId(7);
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setName(planName);
        plan.setTier(SubscriptionTier.ESSENTIALS);
        UserSubscription subscription = UserSubscription.builder()
                .id(99)
                .user(user)
                .plan(plan)
                .status(SubscriptionStatus.PENDING)
                .isTrial(false)
                .expiredAt(expiration)
                .build();
        when(userSubscriptionService.getUserSubById(99)).thenReturn(subscription);
        when(userSubscriptionService.save(subscription)).thenReturn(subscription);
        return new Fixture(subscription);
    }

    private SubscriptionChangeStatusEvent event(
            UserSubscription subscription,
            SubscriptionEventType eventType,
            TradingViewExpirationPolicy policy
    ) {
        SubscriptionChangeStatusEvent event = new SubscriptionChangeStatusEvent();
        event.setEmail("aryansn484@gmail.com");
        event.setTradingViewUsername("aryansn484");
        event.setUserSubscriptionId(subscription.getId());
        event.setEventType(eventType);
        event.setTradingViewExpirationPolicy(policy);
        return event;
    }

    private record Fixture(UserSubscription subscription) {
    }
}
