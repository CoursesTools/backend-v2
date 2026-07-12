package com.winworld.coursestools.service.external;

import com.winworld.coursestools.dto.external.ActivateTradingViewAccessDto;
import com.winworld.coursestools.dto.external.ChangeTradingViewNameDto;
import com.winworld.coursestools.enums.TradingViewRetryJobType;
import com.winworld.coursestools.exception.exceptions.TradingViewUserNotFoundException;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivatingSubscriptionService {
    private final RestTemplate restTemplate;
    private final TradingViewRetryService retryService;

    @Value("${urls.activating-bot}")
    private String activatingBotUrl;

    @Value("${urls.change-tradingview-bot}")
    private String changeTradingViewBotUrl;

    @Retry(name = "client-error-included", fallbackMethod = "handleActivationFallback")
    public void activateTradingViewAccess(Integer userId, ActivateTradingViewAccessDto dto) {
        try {
            restTemplate.postForEntity(activatingBotUrl, dto, Void.class);
        } catch (HttpClientErrorException.NotFound e) {
            // Bot said: TradingView user doesn't exist. Permanent error — don't retry,
            // don't enqueue a PENDING job. TradingViewUserNotFoundException extends
            // DataValidationException, which stays ignore-listed under client-error-included,
            // so it propagates immediately to the caller (admin grant, user self-bind, or
            // async listener) as a clean 400-mappable exception for DEAD surfacing.
            throw new TradingViewUserNotFoundException(dto.getTradingViewName());
        } catch (RestClientResponseException e) {
            // Any OTHER non-2xx (400/422/5xx). Previously HttpClientErrorException was
            // ignore-listed under the "default" retry config, so a 4xx here vanished into the
            // async uncaught-exception handler with the subscription stuck PENDING and no
            // retry row. Capture the bot's real status + body (previously discarded) for
            // diagnosis, then rethrow: client-error-included does NOT ignore these, so @Retry
            // runs and — once exhausted — handleActivationFallback enqueues a durable retry.
            log.error("TV activation rejected by bot: userId={}, name={}, tier={}, expiration={}, status={}, body={}",
                    userId, dto.getTradingViewName(), dto.getTier(), dto.getExpiration(),
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        }
        log.info("TV activation succeeded: userId={}, name={}, tier={}, expiration={}",
                userId, dto.getTradingViewName(), dto.getTier(), dto.getExpiration());
    }

    @Retry(name = "client-error-included", fallbackMethod = "handleRenameFallback")
    public void changeTradingViewUsername(Integer userId, ChangeTradingViewNameDto dto) {
        try {
            restTemplate.postForEntity(changeTradingViewBotUrl, dto, Void.class);
        } catch (HttpClientErrorException.NotFound e) {
            // Bot said: the "old" TradingView user doesn't exist. Nothing to rename.
            throw new TradingViewUserNotFoundException(dto.getOldName());
        } catch (RestClientResponseException e) {
            // Non-404 bot rejection — same silent-channel fix as activateTradingViewAccess:
            // log the real status + body, then rethrow so the rename retry is durable.
            log.error("TV rename rejected by bot: userId={}, {} -> {}, tier={}, expiration={}, status={}, body={}",
                    userId, dto.getOldName(), dto.getNewName(), dto.getTier(), dto.getExpiration(),
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        }
        log.info("TV rename succeeded: userId={}, {} -> {}, tier={}, expiration={}",
                userId, dto.getOldName(), dto.getNewName(), dto.getTier(), dto.getExpiration());
    }

    // Resilience4j fallback: invoked after retries are exhausted. DOES NOT throw —
    // enqueues a durable retry so the caller's @Transactional can still commit the
    // subscription state. The TradingViewRetryScheduler drains the queue.
    @SuppressWarnings("unused")
    public void handleActivationFallback(Integer userId, ActivateTradingViewAccessDto dto, Throwable throwable) {
        log.error("TV activation failed after retries — enqueuing durable retry (userId={}, name={}, exp={})",
                userId, dto.getTradingViewName(), dto.getExpiration(), throwable);
        retryService.enqueue(userId, TradingViewRetryJobType.ACTIVATE, dto, throwable);
    }

    @SuppressWarnings("unused")
    public void handleRenameFallback(Integer userId, ChangeTradingViewNameDto dto, Throwable throwable) {
        log.error("TV rename failed after retries — enqueuing durable retry (userId={}, {} -> {}, exp={})",
                userId, dto.getOldName(), dto.getNewName(), dto.getExpiration(), throwable);
        retryService.enqueue(userId, TradingViewRetryJobType.RENAME, dto, throwable);
    }
}
