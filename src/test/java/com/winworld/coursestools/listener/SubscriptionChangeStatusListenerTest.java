package com.winworld.coursestools.listener;

import com.winworld.coursestools.dto.external.ActivateTradingViewAccessDto;
import com.winworld.coursestools.entity.TradingViewRetryJob;
import com.winworld.coursestools.entity.subscription.SubscriptionPlan;
import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.entity.user.UserSubscription;
import com.winworld.coursestools.enums.Plan;
import com.winworld.coursestools.enums.SubscriptionEventType;
import com.winworld.coursestools.enums.SubscriptionStatus;
import com.winworld.coursestools.enums.SubscriptionTier;
import com.winworld.coursestools.enums.TradingViewExpirationPolicy;
import com.winworld.coursestools.enums.TradingViewDeliveryStatus;
import com.winworld.coursestools.exception.exceptions.TradingViewUserNotFoundException;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
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
    void postgresSubMicrosecondRounding_doesNotDiscardCurrentActivation() {
        LocalDateTime persistedExpiration = LocalDateTime.of(2026, 8, 28, 4, 59, 12, 245_170_000);
        LocalDateTime eventExpiration = persistedExpiration.minusNanos(211);
        var fixture = fixture(persistedExpiration, Plan.MONTH);
        var event = eventSnapshot(
                fixture.subscription(), eventExpiration, SubscriptionEventType.TRIAL_CREATED,
                TradingViewExpirationPolicy.EXACT, "command-99");
        when(activatingSubscriptionService.activateTradingViewAccess(
                eq(7), org.mockito.ArgumentMatchers.any()))
                .thenReturn(TradingViewDeliveryStatus.DELIVERED);

        listener().activateUserSubscription(event);

        verify(activatingSubscriptionService).activateTradingViewAccess(
                eq(7), org.mockito.ArgumentMatchers.any());
        verify(tradingViewRetryService).completeActivation(
                org.mockito.ArgumentMatchers.any(TradingViewRetryJob.class));
        assertThat(fixture.subscription().getStatus()).isEqualTo(SubscriptionStatus.GRANTED);
    }

    @Test
    void currentCommandWithMaterialSnapshotMismatch_movesToDeadInsteadOfDisappearing() {
        LocalDateTime persistedExpiration = LocalDateTime.of(2026, 8, 28, 4, 59, 12, 245_170_000);
        var fixture = fixture(persistedExpiration, Plan.MONTH);
        var event = eventSnapshot(
                fixture.subscription(), persistedExpiration.minusNanos(1_001),
                SubscriptionEventType.TRIAL_CREATED,
                TradingViewExpirationPolicy.EXACT, "command-99");

        listener().activateUserSubscription(event);

        verify(activatingSubscriptionService, never()).activateTradingViewAccess(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(tradingViewRetryService).markActivationDead(
                org.mockito.ArgumentMatchers.any(TradingViewRetryJob.class), anyString());
        verify(tradingViewRetryService, never()).completeActivation(
                org.mockito.ArgumentMatchers.any(TradingViewRetryJob.class));
        assertThat(fixture.subscription().getStatus()).isEqualTo(SubscriptionStatus.PENDING);
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

    @Test
    void oldPaymentEvent_afterNewerAdminCommand_neverBuffersAdminExpiration() {
        LocalDateTime paidExpiration = LocalDateTime.of(2026, 9, 18, 9, 51);
        LocalDateTime adminExpiration = LocalDateTime.of(2026, 10, 5, 0, 0);
        var fixture = fixture(adminExpiration, Plan.MONTH);
        SubscriptionChangeStatusEvent oldPayment = eventSnapshot(
                fixture.subscription(), paidExpiration, SubscriptionEventType.CREATED,
                TradingViewExpirationPolicy.CUSTOMER_PAYMENT_BUFFER, "paid-command");
        SubscriptionChangeStatusEvent newerAdmin = eventSnapshot(
                fixture.subscription(), adminExpiration, SubscriptionEventType.EXTENDED,
                TradingViewExpirationPolicy.EXACT, "admin-command");
        TradingViewRetryJob adminJob = stagedJob("admin-command");
        when(tradingViewRetryService.lockCurrentActivation(7, "paid-command"))
                .thenReturn(Optional.empty());
        when(tradingViewRetryService.lockCurrentActivation(7, "admin-command"))
                .thenReturn(Optional.of(adminJob));
        when(activatingSubscriptionService.activateTradingViewAccess(eq(7), org.mockito.ArgumentMatchers.any()))
                .thenReturn(TradingViewDeliveryStatus.DELIVERED);

        listener().activateUserSubscription(oldPayment);
        listener().activateUserSubscription(newerAdmin);

        ArgumentCaptor<ActivateTradingViewAccessDto> payload =
                ArgumentCaptor.forClass(ActivateTradingViewAccessDto.class);
        verify(activatingSubscriptionService).activateTradingViewAccess(eq(7), payload.capture());
        assertThat(payload.getValue().getExpiration()).isEqualTo(adminExpiration);
        assertThat(fixture.subscription().getStatus()).isEqualTo(SubscriptionStatus.GRANTED);
    }

    @Test
    void oldAdminEvent_afterNewerPaymentCommand_neverExactSendsPaidExpiration() {
        LocalDateTime adminExpiration = LocalDateTime.of(2026, 9, 19, 0, 0);
        LocalDateTime paidExpiration = LocalDateTime.of(2026, 10, 18, 9, 51);
        var fixture = fixture(paidExpiration, Plan.MONTH);
        SubscriptionChangeStatusEvent oldAdmin = eventSnapshot(
                fixture.subscription(), adminExpiration, SubscriptionEventType.EXTENDED,
                TradingViewExpirationPolicy.EXACT, "admin-command");
        SubscriptionChangeStatusEvent newerPayment = eventSnapshot(
                fixture.subscription(), paidExpiration, SubscriptionEventType.EXTENDED,
                TradingViewExpirationPolicy.CUSTOMER_PAYMENT_BUFFER, "paid-command");
        TradingViewRetryJob paidJob = stagedJob("paid-command");
        when(tradingViewRetryService.lockCurrentActivation(7, "admin-command"))
                .thenReturn(Optional.empty());
        when(tradingViewRetryService.lockCurrentActivation(7, "paid-command"))
                .thenReturn(Optional.of(paidJob));
        when(activatingSubscriptionService.activateTradingViewAccess(eq(7), org.mockito.ArgumentMatchers.any()))
                .thenReturn(TradingViewDeliveryStatus.DELIVERED);

        listener().activateUserSubscription(oldAdmin);
        listener().activateUserSubscription(newerPayment);

        ArgumentCaptor<ActivateTradingViewAccessDto> payload =
                ArgumentCaptor.forClass(ActivateTradingViewAccessDto.class);
        verify(activatingSubscriptionService).activateTradingViewAccess(eq(7), payload.capture());
        assertThat(payload.getValue().getExpiration()).isEqualTo(paidExpiration.plusDays(1));
        assertThat(fixture.subscription().getStatus()).isEqualTo(SubscriptionStatus.GRANTED);
    }

    @Test
    void paymentEventSupersededByDirect_reconcilesPendingWithoutSendingPaymentPayload() {
        LocalDateTime paidExpiration = LocalDateTime.of(2026, 9, 18, 9, 51);
        var fixture = fixture(paidExpiration, Plan.MONTH);
        SubscriptionChangeStatusEvent paidEvent = eventSnapshot(
                fixture.subscription(), paidExpiration, SubscriptionEventType.CREATED,
                TradingViewExpirationPolicy.CUSTOMER_PAYMENT_BUFFER, "paid-command");
        when(tradingViewRetryService.lockCurrentActivation(7, "paid-command"))
                .thenReturn(Optional.empty());

        listener().activateUserSubscription(paidEvent);

        verify(activatingSubscriptionService, never()).activateTradingViewAccess(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertThat(fixture.subscription().getStatus()).isEqualTo(SubscriptionStatus.GRANTED);
        verify(userSubscriptionService).save(fixture.subscription());
    }

    @Test
    void supersededEventNeverResurrectsGraceSubscription() {
        LocalDateTime expiration = LocalDateTime.of(2026, 9, 18, 9, 51);
        var fixture = fixture(expiration, Plan.MONTH);
        fixture.subscription().setStatus(SubscriptionStatus.GRACE_PERIOD);
        SubscriptionChangeStatusEvent paidEvent = eventSnapshot(
                fixture.subscription(), expiration, SubscriptionEventType.CREATED,
                TradingViewExpirationPolicy.CUSTOMER_PAYMENT_BUFFER, "paid-command");
        when(tradingViewRetryService.lockCurrentActivation(7, "paid-command"))
                .thenReturn(Optional.empty());

        listener().activateUserSubscription(paidEvent);

        assertThat(fixture.subscription().getStatus()).isEqualTo(SubscriptionStatus.GRACE_PERIOD);
        verify(userSubscriptionService, never()).save(fixture.subscription());
        verify(activatingSubscriptionService, never()).activateTradingViewAccess(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void permanentNotFound_movesStagedCommandToDeadAndDoesNotLeavePending() {
        LocalDateTime expiration = LocalDateTime.of(2026, 9, 18, 9, 51);
        var fixture = fixture(expiration, Plan.MONTH);
        SubscriptionChangeStatusEvent event = eventSnapshot(
                fixture.subscription(), expiration, SubscriptionEventType.CREATED,
                TradingViewExpirationPolicy.CUSTOMER_PAYMENT_BUFFER, "paid-command");
        TradingViewRetryJob job = stagedJob("paid-command");
        when(tradingViewRetryService.lockCurrentActivation(7, "paid-command"))
                .thenReturn(Optional.of(job));
        doThrow(new TradingViewUserNotFoundException("aryansn484"))
                .when(activatingSubscriptionService)
                .activateTradingViewAccess(eq(7), org.mockito.ArgumentMatchers.any());

        listener().activateUserSubscription(event);

        verify(tradingViewRetryService).markActivationDead(
                eq(job), org.mockito.ArgumentMatchers.anyString());
        assertThat(fixture.subscription().getStatus()).isEqualTo(SubscriptionStatus.GRANTED);
    }

    private SubscriptionChangeStatusListener listener() {
        return new SubscriptionChangeStatusListener(
                List.of(), emailService, activatingSubscriptionService,
                userSubscriptionService, tradingViewRetryService);
    }

    private Fixture fixture(LocalDateTime expiration, Plan planName) {
        User user = new User();
        user.setId(7);
        user.setEmail("aryansn484@gmail.com");
        var social = new com.winworld.coursestools.entity.user.UserSocial();
        social.setTradingViewName("aryansn484");
        user.setSocial(social);
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
        when(userSubscriptionService.getUserSubByIdForUpdate(99)).thenReturn(subscription);
        org.mockito.Mockito.lenient().when(userSubscriptionService.save(subscription)).thenReturn(subscription);
        org.mockito.Mockito.lenient().when(tradingViewRetryService.lockCurrentActivation(7, "command-99"))
                .thenReturn(Optional.of(stagedJob("command-99")));
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
        event.setUserId(7);
        event.setExpiration(subscription.getExpiredAt());
        event.setTier(subscription.getPlan().getTier());
        event.setLifetime(subscription.getPlan().getName() == Plan.LIFETIME);
        event.setActivationCommandId("command-99");
        return event;
    }

    private SubscriptionChangeStatusEvent eventSnapshot(
            UserSubscription subscription,
            LocalDateTime expiration,
            SubscriptionEventType eventType,
            TradingViewExpirationPolicy policy,
            String commandId
    ) {
        SubscriptionChangeStatusEvent event = event(subscription, eventType, policy);
        event.setExpiration(expiration);
        event.setActivationCommandId(commandId);
        return event;
    }

    private TradingViewRetryJob stagedJob(String commandId) {
        return TradingViewRetryJob.builder()
                .id(501)
                .commandId(commandId)
                .build();
    }

    private record Fixture(UserSubscription subscription) {
    }
}
