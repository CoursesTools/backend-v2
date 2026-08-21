package com.winworld.coursestools.service;

import com.winworld.coursestools.entity.user.UserSubscription;
import com.winworld.coursestools.enums.PaymentMethod;
import com.winworld.coursestools.enums.SubscriptionStatus;
import com.winworld.coursestools.enums.TradingViewExpirationPolicy;
import com.winworld.coursestools.repository.user.UserSubscriptionRepository;
import com.winworld.coursestools.service.external.TradingViewRetryService;
import com.winworld.coursestools.service.user.UserDataService;
import com.winworld.coursestools.service.user.UserSubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;

import static com.winworld.coursestools.enums.SubscriptionEventType.EXTENDED;

@Service
@RequiredArgsConstructor
@Slf4j
public class PendingSubscriptionReconciliationWorker {
    private static final EnumSet<PaymentMethod> CUSTOMER_PAYMENT_METHODS = EnumSet.of(
            PaymentMethod.CRYPTO,
            PaymentMethod.STRIPE,
            PaymentMethod.PAYEER,
            PaymentMethod.BALANCE
    );

    private final UserSubscriptionRepository userSubscriptionRepository;
    private final UserDataService userDataService;
    private final UserSubscriptionService userSubscriptionService;
    private final TradingViewRetryService tradingViewRetryService;
    private final SubscriptionService subscriptionService;

    @Value("${subscription.ct-pro.trial.days}")
    private int trialDays;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean reconcile(Integer subscriptionId, LocalDateTime cutoffDate, String trigger) {
        UserSubscription candidate = userSubscriptionRepository.findById(subscriptionId).orElse(null);
        if (candidate == null) {
            return false;
        }

        Integer userId = candidate.getUser().getId();
        // Payment processing serializes on the user before mutating a subscription.
        // Keep that order, followed by the established subscription -> ACTIVATE order.
        userDataService.getUserByIdForUpdate(userId);
        UserSubscription subscription = userSubscriptionService.getUserSubByIdForUpdate(subscriptionId);

        if (subscription.getStatus() != SubscriptionStatus.PENDING
                || subscription.getUpdatedAt() == null
                || subscription.getUpdatedAt().isAfter(cutoffDate)) {
            return false;
        }

        int subscriptionTypeId = subscription.getPlan().getSubscriptionType().getId();
        var nonTerminated = userSubscriptionRepository
                .findAllCurrentBySubTypeNotTerminatedWithPlan(subscriptionTypeId, userId);
        // A paid entitlement always supersedes a trial, even if legacy dates would
        // otherwise sort the trial first. Within the same class, repository order
        // remains the application's canonical latest-subscription order.
        var current = nonTerminated.stream()
                .filter(row -> !Boolean.TRUE.equals(row.getIsTrial()))
                .findFirst()
                .orElseGet(() -> nonTerminated.stream().findFirst().orElse(null));
        if (current == null || !subscription.getId().equals(current.getId())) {
            log.warn(
                    "Stuck PENDING reconciliation skipped superseded subscription: "
                            + "trigger={} userId={} subscriptionId={} currentSubscriptionId={}",
                    trigger,
                    userId,
                    subscriptionId,
                    current == null ? null : current.getId()
            );
            return false;
        }

        if (tradingViewRetryService.hasPendingOrDeadActivation(userId)) {
            log.info(
                    "Stuck PENDING reconciliation left existing TV command to retry/admin handling: "
                            + "trigger={} userId={} subscriptionId={}",
                    trigger,
                    userId,
                    subscriptionId
            );
            return false;
        }

        if (Boolean.TRUE.equals(subscription.getIsTrial())) {
            subscription.setExpiredAt(nowUtc().plusDays(trialDays));
            userSubscriptionService.save(subscription);
        }

        TradingViewExpirationPolicy policy = expirationPolicy(subscription);
        subscriptionService.publishSubscriptionEvent(
                subscription.getUser(), EXTENDED, subscription, policy);
        log.warn(
                "Re-staged orphaned PENDING subscription activation: trigger={} userId={} "
                        + "subscriptionId={} trial={} paymentMethod={} expiration={} policy={}",
                trigger,
                userId,
                subscriptionId,
                subscription.getIsTrial(),
                subscription.getPaymentMethod(),
                subscription.getExpiredAt(),
                policy
        );
        return true;
    }

    private TradingViewExpirationPolicy expirationPolicy(UserSubscription subscription) {
        if (!Boolean.TRUE.equals(subscription.getIsTrial())
                && subscription.getPaymentMethod() != null
                && CUSTOMER_PAYMENT_METHODS.contains(subscription.getPaymentMethod())) {
            return TradingViewExpirationPolicy.CUSTOMER_PAYMENT_BUFFER;
        }
        return TradingViewExpirationPolicy.EXACT;
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC)
                .plusNanos(500)
                .truncatedTo(ChronoUnit.MICROS);
    }
}
