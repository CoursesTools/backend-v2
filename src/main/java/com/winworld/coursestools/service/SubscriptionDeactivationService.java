package com.winworld.coursestools.service;

import com.winworld.coursestools.entity.Referral;
import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.entity.user.UserSubscription;
import com.winworld.coursestools.exception.exceptions.EntityNotFoundException;
import com.winworld.coursestools.mapper.SubscriptionMapper;
import com.winworld.coursestools.repository.user.UserSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static com.winworld.coursestools.enums.PaymentMethod.STRIPE;
import static com.winworld.coursestools.enums.SubscriptionEventType.GRACE_PERIOD_END;
import static com.winworld.coursestools.enums.SubscriptionEventType.GRACE_PERIOD_START;
import static com.winworld.coursestools.enums.SubscriptionStatus.GRACE_PERIOD;
import static com.winworld.coursestools.enums.SubscriptionStatus.TERMINATED;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionDeactivationService {
    private final ApplicationEventPublisher eventPublisher;
    private final SubscriptionMapper subscriptionMapper;
    private final UserSubscriptionRepository userSubscriptionRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deactivateSingleSubscription(int userSubscriptionId) {
        UserSubscription userSubscription = getUserSubscriptionForUpdate(userSubscriptionId);
        userSubscription.setStatus(GRACE_PERIOD);
        User user = userSubscription.getUser();
        Referral referred = user.getReferred();
        if (referred != null) {
            referred.setActive(false);
        }
        logSkippedStripeCancellation(userSubscription);
        log.info("User {} subscription expired", user.getId());
        eventPublisher.publishEvent(subscriptionMapper.toEvent(user, GRACE_PERIOD_START, userSubscription));
        //TODO Сделать напоминание о 3 днях, 7 и т.д.
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void terminatePastGracePeriodSubscription(int userSubscriptionId) {
        UserSubscription userSubscription = getUserSubscriptionForUpdate(userSubscriptionId);
        if (userSubscription.getStatus() == TERMINATED) {
            return;
        }
        var previousStatus = userSubscription.getStatus();

        User user = userSubscription.getUser();
        Referral referred = user.getReferred();
        if (referred != null) {
            referred.setActive(false);
        }
        logSkippedStripeCancellation(userSubscription);

        userSubscription.setStatus(TERMINATED);
        log.warn(
                "User {} subscription {} terminated after grace-period reconciliation from status {}",
                user.getId(),
                userSubscription.getId(),
                previousStatus
        );
        eventPublisher.publishEvent(subscriptionMapper.toEvent(user, GRACE_PERIOD_END, userSubscription));
    }

    private void logSkippedStripeCancellation(UserSubscription userSubscription) {
        if (!userSubscription.getIsTrial() && STRIPE.equals(userSubscription.getPaymentMethod())) {
            log.warn(
                    "Subscription {} is Stripe-backed; local expiry reconciliation will not cancel Stripe subscription",
                    userSubscription.getId()
            );
        }
    }

    private UserSubscription getUserSubscriptionForUpdate(int userSubscriptionId) {
        return userSubscriptionRepository.findByIdWithUserDetails(userSubscriptionId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "User subscription not found with id: " + userSubscriptionId
                ));
    }
}
