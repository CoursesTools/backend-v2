package com.winworld.coursestools.service.external;

import com.winworld.coursestools.dto.external.ActivateTradingViewAccessDto;
import com.winworld.coursestools.dto.external.ChangeTradingViewNameDto;
import com.winworld.coursestools.enums.TradingViewRetryJobType;
import com.winworld.coursestools.enums.TradingViewDeliveryStatus;
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

    @Retry(name = "default", fallbackMethod = "handleActivationFallback")
    public TradingViewDeliveryStatus activateTradingViewAccess(Integer userId, ActivateTradingViewAccessDto dto) {
        try {
            restTemplate.postForEntity(activatingBotUrl, dto, Void.class);
        } catch (HttpClientErrorException.NotFound e) {
            // Bot said the TradingView user doesn't exist. Permanent input error:
            // TradingViewUserNotFoundException extends DataValidationException, which the
            // retry config ignore-lists, so it is not re-attempted.
            throw new TradingViewUserNotFoundException(dto.getTradingViewName());
        } catch (RestClientResponseException e) {
            // Any other non-2xx from the bot (400/422/5xx). The bot's status + response
            // body were previously discarded, leaving these failures undiagnosable in logs
            // and on the admin retry page. Capture them here, then rethrow so the existing
            // @Retry + durable handleActivationFallback machinery still handles the failure.
            log.error("TV activation rejected by bot: userId={}, name={}, tier={}, expiration={}, status={}, body={}",
                    userId, dto.getTradingViewName(), dto.getTier(), dto.getExpiration(),
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        }
        log.info("TV activation succeeded: userId={}, name={}, tier={}, expiration={}",
                userId, dto.getTradingViewName(), dto.getTier(), dto.getExpiration());
        return TradingViewDeliveryStatus.DELIVERED;
    }

    @Retry(name = "default", fallbackMethod = "handleRenameFallback")
    public void changeTradingViewUsername(Integer userId, ChangeTradingViewNameDto dto) {
        try {
            restTemplate.postForEntity(changeTradingViewBotUrl, dto, Void.class);
        } catch (HttpClientErrorException.NotFound e) {
            // Bot said: the "old" TradingView user doesn't exist. Nothing to rename.
            throw new TradingViewUserNotFoundException(dto.getOldName());
        } catch (RestClientResponseException e) {
            // Non-404 bot rejection — same diagnostics as activateTradingViewAccess:
            // capture the bot's status + body (previously discarded), then rethrow so the
            // existing @Retry + durable handleRenameFallback machinery still handles it.
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
    public TradingViewDeliveryStatus handleActivationFallback(
            Integer userId,
            ActivateTradingViewAccessDto dto,
            Throwable throwable
    ) {
        log.error("TV activation failed after retries — enqueuing durable retry (userId={}, name={}, exp={})",
                userId, dto.getTradingViewName(), dto.getExpiration(), throwable);
        retryService.enqueue(userId, TradingViewRetryJobType.ACTIVATE, dto, throwable);
        return TradingViewDeliveryStatus.QUEUED;
    }

    @SuppressWarnings("unused")
    public void handleRenameFallback(Integer userId, ChangeTradingViewNameDto dto, Throwable throwable) {
        log.error("TV rename failed after retries — enqueuing durable retry (userId={}, {} -> {}, exp={})",
                userId, dto.getOldName(), dto.getNewName(), dto.getExpiration(), throwable);
        retryService.enqueue(userId, TradingViewRetryJobType.RENAME, dto, throwable);
    }

    // resilience4j's fallback catches EVERY throwable — including the ignore-listed
    // TradingViewUserNotFoundException (a 404: the nickname doesn't exist on TradingView).
    // Without these more-specific overloads that permanent error would be swallowed here and
    // mis-enqueued as a transient PENDING retry, so the caller's handling never runs: the
    // async listener's immediate DEAD-surfacing, and the 400 mapping for admin grants / user
    // self-bind. FallbackMethod dispatches to the most specific overload by thrown type, so a
    // 404 rethrows to the caller while transient failures fall through to the Throwable
    // overloads above that enqueue a durable retry.
    @SuppressWarnings("unused")
    public TradingViewDeliveryStatus handleActivationFallback(
            Integer userId,
            ActivateTradingViewAccessDto dto,
            TradingViewUserNotFoundException e
    ) {
        throw e;
    }

    @SuppressWarnings("unused")
    public void handleRenameFallback(Integer userId, ChangeTradingViewNameDto dto,
                                     TradingViewUserNotFoundException e) {
        throw e;
    }
}
