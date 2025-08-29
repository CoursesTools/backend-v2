package com.winworld.coursestools.service.external;

import com.winworld.coursestools.dto.external.ActivateSubscriptionDto;
import com.winworld.coursestools.exception.exceptions.ExternalServiceException;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivatingSubscriptionService {
    private final RestTemplate restTemplate;

    @Value("${urls.activating-bot}")
    private String activatingBotUrl;

    @Value("${secrets.activate-subscription-secret}")
    private String secret;

    @Retry(name = "default", fallbackMethod = "handleFallback")
    public void activateSubscription(ActivateSubscriptionDto dto) {
        //TODO сделать HMAC
        dto.setSecret(secret);
        restTemplate.postForEntity(activatingBotUrl, dto, Void.class);
        log.info("Subscription activated for name: {}, expiration: {}", dto.getTradingViewName(), dto.getExpiration());
    }

    public void handleFallback(ActivateSubscriptionDto dto, Throwable throwable) {
        //TODO сделать алерт
        log.error("Failed to activate subscription for name: {}, expiration: {}",
                  dto.getTradingViewName(), dto.getExpiration(), throwable);
        throw new ExternalServiceException("Failed to activate subscription");
    }
}
