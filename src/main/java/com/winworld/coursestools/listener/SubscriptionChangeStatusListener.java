package com.winworld.coursestools.listener;

import com.winworld.coursestools.dto.external.ActivateSubscriptionDto;
import com.winworld.coursestools.enums.SubscriptionEventType;
import com.winworld.coursestools.enums.SubscriptionStatus;
import com.winworld.coursestools.event.SubscriptionChangeStatusEvent;
import com.winworld.coursestools.messaging.MessageBuilder;
import com.winworld.coursestools.service.ActivatingSubscriptionService;
import com.winworld.coursestools.service.EmailService;
import com.winworld.coursestools.service.user.UserSubscriptionService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Component
public class SubscriptionChangeStatusListener extends AbstractNotificationListener<SubscriptionChangeStatusEvent> {
    private final ActivatingSubscriptionService activatingSubscriptionService;
    private final UserSubscriptionService userSubscriptionService;

    private static final List<SubscriptionEventType> EVENTS_FOR_ACTIVATE = List.of(
            SubscriptionEventType.CREATED,
            SubscriptionEventType.TRIAL_CREATED,
            SubscriptionEventType.EXTENDED,
            SubscriptionEventType.RESTORED
    );

    public SubscriptionChangeStatusListener(
            List<MessageBuilder<SubscriptionChangeStatusEvent>> messageBuilders,
            EmailService emailService,
            ActivatingSubscriptionService activatingSubscriptionService, UserSubscriptionService userSubscriptionService) {
        super(messageBuilders, emailService);
        this.activatingSubscriptionService = activatingSubscriptionService;
        this.userSubscriptionService = userSubscriptionService;
    }

    @TransactionalEventListener
    @Async
    public void activateUserSubscription(SubscriptionChangeStatusEvent event) {
        if (!EVENTS_FOR_ACTIVATE.contains(event.getEventType())) {
            return;
        }
        var userSubscription = userSubscriptionService.getUserSubById(event.getUserSubscriptionId());
        ActivateSubscriptionDto dto = new ActivateSubscriptionDto(
                event.getEmail(), event.getTradingViewUsername(), userSubscription.getExpiredAt());
        activatingSubscriptionService.activateSubscription(dto);
        userSubscription.setStatus(SubscriptionStatus.GRANTED);
        userSubscriptionService.save(userSubscription);
    }

    @TransactionalEventListener
    @Async
    public void sendNotificationEmail(SubscriptionChangeStatusEvent event) {
        sendEmails(event.getEmail(), event);
    }
}
