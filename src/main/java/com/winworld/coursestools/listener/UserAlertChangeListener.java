package com.winworld.coursestools.listener;

import com.winworld.coursestools.event.UserAlertsChangeEvent;
import com.winworld.coursestools.messaging.MessageBuilder;
import com.winworld.coursestools.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

@Component
@Slf4j
public class UserAlertChangeListener extends AbstractNotificationListener<UserAlertsChangeEvent> {
    private final RestTemplate restTemplate;

    @Value("${urls.alert-bot}")
    private String alertBotUrl;

    @Value("${ops.alert-bot.internal-token}")
    private String alertBotInternalToken;

    public UserAlertChangeListener(List<MessageBuilder<UserAlertsChangeEvent>> messageBuilders, EmailService emailService, RestTemplate restTemplate) {
        super(messageBuilders, emailService);
        this.restTemplate = restTemplate;
    }

    @TransactionalEventListener
    @Async
    public void handleUserAlertChangeEvent(UserAlertsChangeEvent event) {
        if (!StringUtils.hasText(event.getTelegramId())) {
            log.warn("Skipping alert-bot subscription confirmation: user has no Telegram id");
            return;
        }
        sendNotificationAboutSubscription(event.getTelegramId());
    }

    private void sendNotificationAboutSubscription(String telegramId) {
        URI uri = UriComponentsBuilder.fromHttpUrl(alertBotUrl)
                .queryParam("telegramId", telegramId)
                .build()
                .toUri();

        HttpHeaders headers = new HttpHeaders();
        if (StringUtils.hasText(alertBotInternalToken)) {
            headers.set("X-Internal-Token", alertBotInternalToken);
        }
        restTemplate.postForObject(uri, new HttpEntity<>(null, headers), Void.class);
    }
}
