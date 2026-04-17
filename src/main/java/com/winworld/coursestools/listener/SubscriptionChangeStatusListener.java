package com.winworld.coursestools.listener;

import com.winworld.coursestools.dto.external.ActivateTradingViewAccessDto;
import com.winworld.coursestools.enums.SubscriptionEventType;
import com.winworld.coursestools.enums.SubscriptionStatus;
import com.winworld.coursestools.enums.TradingViewRetryJobType;
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

import java.util.List;

@Slf4j
@Component
public class SubscriptionChangeStatusListener extends AbstractNotificationListener<SubscriptionChangeStatusEvent> {
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
        var userSubscription = userSubscriptionService.getUserSubById(event.getUserSubscriptionId());
        Integer userId = userSubscription.getUser().getId();
        ActivateTradingViewAccessDto dto = new ActivateTradingViewAccessDto(
                event.getEmail(), userSubscription.getPlan().getTier(),
                event.getTradingViewUsername(), userSubscription.getExpiredAt());
        try {
            activatingSubscriptionService.activateTradingViewAccess(userId, dto);
        } catch (TradingViewUserNotFoundException e) {
            // Permanent error — the user's nickname doesn't exist on TradingView.
            // The producing transaction (payment webhook / trial creation) has already
            // committed the subscription row. Grant access in DB (customer paid, they
            // deserve it) and enqueue a DEAD retry so the admin's TV retry page
            // surfaces "nickname invalid — action required" for an operator to fix.
            log.error("TV activation failed permanently (nickname not found) for userId={} sub={} tv={}; " +
                            "subscription will be marked GRANTED and a DEAD retry row will surface to admin",
                    userId, userSubscription.getId(), event.getTradingViewUsername(), e);
            tradingViewRetryService.enqueueDead(userId, TradingViewRetryJobType.ACTIVATE, dto, e.getMessage());
        }
        userSubscription.setStatus(SubscriptionStatus.GRANTED);
        userSubscriptionService.save(userSubscription);
    }

    @TransactionalEventListener
    @Async
    public void sendNotificationEmail(SubscriptionChangeStatusEvent event) {
//        sendEmails(event.getEmail(), event);
    }
}
