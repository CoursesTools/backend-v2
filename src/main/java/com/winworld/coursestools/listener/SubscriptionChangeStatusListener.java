package com.winworld.coursestools.listener;

import com.winworld.coursestools.dto.external.ActivateTradingViewAccessDto;
import com.winworld.coursestools.entity.user.UserSubscription;
import com.winworld.coursestools.enums.Plan;
import com.winworld.coursestools.enums.SubscriptionEventType;
import com.winworld.coursestools.enums.SubscriptionStatus;
import com.winworld.coursestools.enums.TradingViewDeliveryStatus;
import com.winworld.coursestools.event.SubscriptionChangeStatusEvent;
import com.winworld.coursestools.exception.exceptions.TradingViewUserNotFoundException;
import com.winworld.coursestools.messaging.MessageBuilder;
import com.winworld.coursestools.service.external.ActivatingSubscriptionService;
import com.winworld.coursestools.service.external.TradingViewRetryService;
import com.winworld.coursestools.service.EmailService;
import com.winworld.coursestools.service.user.UserSubscriptionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
public class SubscriptionChangeStatusListener extends AbstractNotificationListener<SubscriptionChangeStatusEvent> {
    private static final Duration DATABASE_TIMESTAMP_TOLERANCE = Duration.ofNanos(1_000);
    private final ActivatingSubscriptionService activatingSubscriptionService;
    private final UserSubscriptionService userSubscriptionService;
    private final TradingViewRetryService tradingViewRetryService;

    private static final List<SubscriptionEventType> EVENTS_FOR_ACTIVATE = List.of(
            SubscriptionEventType.CREATED,
            SubscriptionEventType.TRIAL_CREATED,
            SubscriptionEventType.EXTENDED,
            SubscriptionEventType.RESTORED
    );

    public SubscriptionChangeStatusListener(
            List<MessageBuilder<SubscriptionChangeStatusEvent>> messageBuilders,
            EmailService emailService,
            ActivatingSubscriptionService activatingSubscriptionService,
            UserSubscriptionService userSubscriptionService,
            TradingViewRetryService tradingViewRetryService) {
        super(messageBuilders, emailService);
        this.activatingSubscriptionService = activatingSubscriptionService;
        this.userSubscriptionService = userSubscriptionService;
        this.tradingViewRetryService = tradingViewRetryService;
    }

    @TransactionalEventListener
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void activateUserSubscription(SubscriptionChangeStatusEvent event) {
        if (!EVENTS_FOR_ACTIVATE.contains(event.getEventType())) {
            return;
        }
        // Producers mutate/lock the subscription before staging the outbox row.
        // Keep the same lock order here to avoid deadlocks and stale-entity saves.
        var userSubscription = userSubscriptionService.getUserSubByIdForUpdate(event.getUserSubscriptionId());
        Integer userId = event.getUserId();
        var staged = tradingViewRetryService.lockCurrentActivation(
                userId, event.getActivationCommandId());
        if (staged.isEmpty()) {
            reconcilePendingStatus(event, userSubscription);
            log.info("Skipping superseded TV activation: userId={} sub={} commandId={}",
                    userId, event.getUserSubscriptionId(), event.getActivationCommandId());
            return;
        }
        if (!isCurrentActivationApplicable(event, userSubscription)) {
            String mismatch = describeSnapshotMismatch(event, userSubscription);
            tradingViewRetryService.markActivationDead(staged.get(), mismatch);
            log.error("TV activation snapshot mismatch moved current command to DEAD: "
                            + "userId={} sub={} commandId={} currentStatus={} mismatch={}",
                    userId, event.getUserSubscriptionId(), event.getActivationCommandId(),
                    userSubscription.getStatus(), mismatch);
            return;
        }

        ActivateTradingViewAccessDto dto = ActivateTradingViewAccessDto.fromSubscriptionEvent(event);
        try {
            var deliveryStatus = activatingSubscriptionService.activateTradingViewAccess(userId, dto);
            if (deliveryStatus == TradingViewDeliveryStatus.DELIVERED) {
                tradingViewRetryService.completeActivation(staged.get());
            }
        } catch (TradingViewUserNotFoundException e) {
            // Permanent error — the user's nickname doesn't exist on TradingView.
            // The producing transaction (payment webhook / trial creation) has already
            // committed the subscription row. Grant access in DB (customer paid, they
            // deserve it) and enqueue a DEAD retry so the admin's TV retry page
            // surfaces "nickname invalid — action required" for an operator to fix.
            log.error("TV activation failed permanently (nickname not found) for userId={} sub={} tv={}; " +
                            "subscription will be marked GRANTED and a DEAD retry row will surface to admin",
                    userId, userSubscription.getId(), event.getTradingViewUsername(), e);
            tradingViewRetryService.markActivationDead(staged.get(), e.getMessage());
        }
        reconcilePendingStatus(event, userSubscription);
    }

    private boolean isCurrentActivationApplicable(
            SubscriptionChangeStatusEvent event,
            UserSubscription subscription
    ) {
        if (subscription.getStatus() != SubscriptionStatus.PENDING
                && subscription.getStatus() != SubscriptionStatus.GRANTED) {
            return false;
        }
        var user = subscription.getUser();
        boolean lifetime = subscription.getPlan().getName() == Plan.LIFETIME;
        return sameDatabaseTimestamp(subscription.getExpiredAt(), event.getExpiration())
                && subscription.getPlan().getTier() == event.getTier()
                && lifetime == event.isLifetime()
                && Objects.equals(user.getEmail(), event.getEmail())
                && Objects.equals(user.getSocial().getTradingViewName(), event.getTradingViewUsername());
    }

    private boolean sameDatabaseTimestamp(LocalDateTime persisted, LocalDateTime snapshot) {
        if (persisted == null || snapshot == null) {
            return persisted == snapshot;
        }
        return Duration.between(persisted, snapshot).abs().compareTo(DATABASE_TIMESTAMP_TOLERANCE) <= 0;
    }

    private String describeSnapshotMismatch(
            SubscriptionChangeStatusEvent event,
            UserSubscription subscription
    ) {
        var user = subscription.getUser();
        boolean lifetime = subscription.getPlan().getName() == Plan.LIFETIME;
        return "expiration[current=" + subscription.getExpiredAt() + ", event=" + event.getExpiration() + "]"
                + ", tier[current=" + subscription.getPlan().getTier() + ", event=" + event.getTier() + "]"
                + ", lifetime[current=" + lifetime + ", event=" + event.isLifetime() + "]"
                + ", emailMatch=" + Objects.equals(user.getEmail(), event.getEmail())
                + ", tv[current=" + user.getSocial().getTradingViewName()
                + ", event=" + event.getTradingViewUsername() + "]";
    }

    private void reconcilePendingStatus(
            SubscriptionChangeStatusEvent event,
            UserSubscription subscription
    ) {
        if (subscription.getStatus() == SubscriptionStatus.PENDING
                && isCurrentActivationApplicable(event, subscription)) {
            subscription.setStatus(SubscriptionStatus.GRANTED);
            userSubscriptionService.save(subscription);
        }
    }

    @TransactionalEventListener
    @Async
    public void sendNotificationEmail(SubscriptionChangeStatusEvent event) {
//        sendEmails(event.getEmail(), event);
    }
}
