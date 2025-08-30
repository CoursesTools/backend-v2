package com.winworld.coursestools.service.external;

import com.winworld.coursestools.dto.external.ActivateTradingViewAccessDto;
import com.winworld.coursestools.dto.external.ChangeTradingViewNameDto;
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

    @Value("${urls.change-tradingview-bot}")
    private String changeTradingViewBotUrl;

    @Retry(name = "default", fallbackMethod = "handleFallback")
    public void activateTradingViewAccess(ActivateTradingViewAccessDto dto) {
        //TODO сделать HMAC
        restTemplate.postForEntity(activatingBotUrl, dto, Void.class);
        log.info("Subscription activated for name: {}, expiration: {}", dto.getTradingViewName(), dto.getExpiration());
    }

    @Retry(name = "default", fallbackMethod = "handleFallback")
    public void changeTradingViewUsername(ChangeTradingViewNameDto dto) {
        //TODO сделать HMAC
        restTemplate.postForEntity(changeTradingViewBotUrl, dto, Void.class);
        log.info("Trading view name changed from {} to {}, expiration: {}", dto.getOldName(), dto.getNewName(), dto.getExpiration());
    }

    public void handleFallback(ActivateTradingViewAccessDto dto, Throwable throwable) {
        //TODO сделать алерт
        log.error("Failed to activate subscription for name: {}, expiration: {}",
                  dto.getTradingViewName(), dto.getExpiration(), throwable);
        throw new ExternalServiceException("Failed to activate subscription");
    }
}
