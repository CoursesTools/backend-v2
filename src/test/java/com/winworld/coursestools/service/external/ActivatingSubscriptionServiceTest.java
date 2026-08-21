package com.winworld.coursestools.service.external;

import com.winworld.coursestools.dto.external.ActivateTradingViewAccessDto;
import com.winworld.coursestools.dto.external.ChangeTradingViewNameDto;
import com.winworld.coursestools.enums.SubscriptionTier;
import com.winworld.coursestools.enums.TradingViewRetryJobType;
import com.winworld.coursestools.enums.TradingViewDeliveryStatus;
import com.winworld.coursestools.exception.exceptions.TradingViewUserNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivatingSubscriptionServiceTest {

    @Mock
    RestTemplate restTemplate;
    @Mock
    TradingViewRetryService retryService;
    @InjectMocks
    ActivatingSubscriptionService service;

    private ActivateTradingViewAccessDto activateDto() {
        return ActivateTradingViewAccessDto.exactGrant("e@x.com", SubscriptionTier.PRO, "nick",
                LocalDateTime.of(2026, 8, 11, 15, 41, 38), false);
    }

    private ChangeTradingViewNameDto renameDto() {
        return ChangeTradingViewNameDto.rename("old", "new", SubscriptionTier.PRO,
                LocalDateTime.of(2026, 8, 11, 15, 41, 38), false);
    }

    @Test
    void activationReturnsDeliveredAfterBotAcceptsPayload() {
        var dto = activateDto();
        ReflectionTestUtils.setField(service, "activatingBotUrl", "http://tv-bot/open");
        when(restTemplate.postForEntity(eq("http://tv-bot/open"), eq(dto), eq(Void.class)))
                .thenReturn(ResponseEntity.ok().build());

        assertThat(service.activateTradingViewAccess(1, dto))
                .isEqualTo(TradingViewDeliveryStatus.DELIVERED);
    }

    @Test
    void activationReturnsDeliveredAfterBodylessNoContentResponse() {
        var dto = activateDto();
        ReflectionTestUtils.setField(service, "activatingBotUrl", "http://tv-bot/open");
        when(restTemplate.postForEntity(eq("http://tv-bot/open"), eq(dto), eq(Void.class)))
                .thenReturn(ResponseEntity.noContent().build());

        assertThat(service.activateTradingViewAccess(1, dto))
                .isEqualTo(TradingViewDeliveryStatus.DELIVERED);
    }

    // The specific TradingViewUserNotFoundException overload must RETHROW so the caller
    // (async listener -> enqueueDead; admin/self-bind -> 400) handles the permanent 404,
    // instead of the generic fallback swallowing it into a transient PENDING retry.
    @Test
    void activationFallback_rethrows_forNotFound_soCallerCanSurfaceDead() {
        var dto = activateDto();
        var notFound = new TradingViewUserNotFoundException("nick");
        assertThatThrownBy(() -> service.handleActivationFallback(1, dto, notFound))
                .isSameAs(notFound);
        verifyNoInteractions(retryService);
    }

    @Test
    void activationFallback_enqueues_forTransientError() {
        var dto = activateDto();
        var transientError = new RuntimeException("connection reset");
        assertThat(service.handleActivationFallback(1, dto, transientError))
                .isEqualTo(TradingViewDeliveryStatus.QUEUED);
        verify(retryService).enqueue(1, TradingViewRetryJobType.ACTIVATE, dto, transientError);
    }

    @Test
    void renameFallback_rethrows_forNotFound() {
        var dto = renameDto();
        var notFound = new TradingViewUserNotFoundException("old");
        assertThatThrownBy(() -> service.handleRenameFallback(1, dto, notFound))
                .isSameAs(notFound);
        verifyNoInteractions(retryService);
    }

    @Test
    void renameFallback_enqueues_forTransientError() {
        var dto = renameDto();
        var transientError = new RuntimeException("timeout");
        service.handleRenameFallback(1, dto, transientError);
        verify(retryService).enqueue(1, TradingViewRetryJobType.RENAME, dto, transientError);
    }
}
