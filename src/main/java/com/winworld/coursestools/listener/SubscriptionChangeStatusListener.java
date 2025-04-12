package com.winworld.coursestools.listener;

import com.winworld.coursestools.event.SubscriptionChangeStatusEvent;
import com.winworld.coursestools.messaging.MessageBuilder;
import com.winworld.coursestools.service.EmailService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Component
public class SubscriptionChangeStatusListener extends AbstractNotificationListener<SubscriptionChangeStatusEvent> {
    public SubscriptionChangeStatusListener(
            List<MessageBuilder<SubscriptionChangeStatusEvent>> messageBuilders,
            EmailService emailService
    ) {
        super(messageBuilders, emailService);
    }

    //TODO Добавить активацию пользователя

    @TransactionalEventListener
    @Async
    public void sendNotificationEmail(SubscriptionChangeStatusEvent event) {
        sendEmails(event.getEmail(), event); //TODO Сделать messageBuilders
    }
}
