package com.winworld.coursestools.listener;

import com.winworld.coursestools.event.UserAlertsChangeEvent;
import com.winworld.coursestools.messaging.MessageBuilder;
import com.winworld.coursestools.service.EmailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

@Component
public class UserAlertChangeListener extends AbstractNotificationListener<UserAlertsChangeEvent> {
    private final RestTemplate restTemplate;

    @Value("${urls.alert-bot}")
    private String alertBotUrl;

    public UserAlertChangeListener(List<MessageBuilder<UserAlertsChangeEvent>> messageBuilders, EmailService emailService, RestTemplate restTemplate) {
        super(messageBuilders, emailService);
        this.restTemplate = restTemplate;
    }

    @TransactionalEventListener
    @Async
    public void handleUserAlertChangeEvent(UserAlertsChangeEvent event) {
        sendNotificationAboutSubscription(event.getTelegramId());
    }

    private void sendNotificationAboutSubscription(String telegramId) {
        URI uri = UriComponentsBuilder.fromHttpUrl(alertBotUrl)
                .queryParam("telegramId", telegramId)
                .build()
                .toUri();

        restTemplate.postForObject(uri, null, Void.class);
    }
}
