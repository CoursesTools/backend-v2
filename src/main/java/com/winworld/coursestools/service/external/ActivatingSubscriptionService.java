package com.winworld.coursestools.service.external;

import com.winworld.coursestools.dto.external.ActivateTradingViewAccessDto;
import com.winworld.coursestools.dto.external.ChangeTradingViewNameDto;
import com.winworld.coursestools.exception.exceptions.ExternalServiceException;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
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
        throw new ExternalServiceException(buildActivationError(dto.getTradingViewName(), throwable));
    }

    public void handleFallback(ChangeTradingViewNameDto dto, Throwable throwable) {
        //TODO сделать алерт
        log.error("Failed to change trading view name from {} to {}, expiration: {}",
                dto.getOldName(), dto.getNewName(), dto.getExpiration(), throwable);
        throw new ExternalServiceException(buildNameChangeError(dto.getOldName(), dto.getNewName(), throwable));
    }

    private String buildActivationError(String tradingViewName, Throwable cause) {
        if (cause instanceof ResourceAccessException) {
            return "TradingView bot is unreachable — cannot activate access for '" + tradingViewName + "'. Check that the bot is running.";
        }
        if (cause instanceof HttpClientErrorException httpEx) {
            if (httpEx.getStatusCode() == HttpStatus.NOT_FOUND) {
                return "TradingView username '" + tradingViewName + "' was not found by the bot. Verify the username is correct.";
            }
            return "TradingView bot rejected activation for '" + tradingViewName + "': " + httpEx.getStatusCode() + " — " + httpEx.getResponseBodyAsString();
        }
        if (cause instanceof HttpServerErrorException httpEx) {
            return "TradingView bot returned a server error while activating '" + tradingViewName + "': " + httpEx.getStatusCode();
        }
        return "Failed to activate TradingView access for '" + tradingViewName + "': " + cause.getMessage();
    }

    private String buildNameChangeError(String oldName, String newName, Throwable cause) {
        if (cause instanceof ResourceAccessException) {
            return "TradingView bot is unreachable — cannot rename '" + oldName + "' → '" + newName + "'. Check that the bot is running.";
        }
        if (cause instanceof HttpClientErrorException httpEx) {
            if (httpEx.getStatusCode() == HttpStatus.NOT_FOUND) {
                return "TradingView username '" + oldName + "' was not found by the bot. Verify the username is correct.";
            }
            return "TradingView bot rejected name change '" + oldName + "' → '" + newName + "': " + httpEx.getStatusCode() + " — " + httpEx.getResponseBodyAsString();
        }
        if (cause instanceof HttpServerErrorException httpEx) {
            return "TradingView bot returned a server error for name change '" + oldName + "' → '" + newName + "': " + httpEx.getStatusCode();
        }
        return "Failed to change TradingView name '" + oldName + "' → '" + newName + "': " + cause.getMessage();
    }
}
