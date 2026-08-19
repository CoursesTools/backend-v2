package com.winworld.coursestools.service.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.winworld.coursestools.dto.external.ActivateTradingViewAccessDto;
import com.winworld.coursestools.entity.TradingViewRetryJob;
import com.winworld.coursestools.enums.SubscriptionTier;
import com.winworld.coursestools.enums.TradingViewRetryJobStatus;
import com.winworld.coursestools.enums.TradingViewRetryJobType;
import com.winworld.coursestools.repository.TradingViewRetryJobRepository;
import com.winworld.coursestools.service.user.UserDataService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradingViewRetryServiceTest {
    @Mock
    private TradingViewRetryJobRepository repository;
    @Mock
    private UserDataService userDataService;
    @Mock
    private RestTemplate restTemplate;

    @Test
    void stageActivationAtomicallyReplacesPendingAndRemovesObsoleteDeadCommand() {
        TradingViewRetryService service = service();
        ActivateTradingViewAccessDto payload = ActivateTradingViewAccessDto.exactGrant(
                "user@example.com", SubscriptionTier.ESSENTIALS, "tv-user",
                LocalDateTime.of(2026, 9, 19, 0, 0), false);

        String commandId = service.stageActivation(7, payload);

        assertThat(UUID.fromString(commandId)).isNotNull();
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(repository).stagePendingActivation(eq(7), json.capture(), eq(commandId), any());
        assertThat(json.getValue()).contains("user@example.com", "2026-09-19T00:00:00");
        verify(repository).deleteByUserIdAndTypeAndStatus(
                7, TradingViewRetryJobType.ACTIVATE, TradingViewRetryJobStatus.DEAD);
    }

    @Test
    void lockCurrentActivationRejectsSupersededCommandId() {
        TradingViewRetryJob current = TradingViewRetryJob.builder()
                .id(10)
                .commandId("new-command")
                .build();
        when(repository.findByUserIdAndTypeAndStatusForUpdate(
                7, TradingViewRetryJobType.ACTIVATE, TradingViewRetryJobStatus.PENDING))
                .thenReturn(Optional.of(current));

        assertThat(service().lockCurrentActivation(7, "old-command")).isEmpty();
    }

    @Test
    void permanentFailureConvertsTheStagedRowToDead() {
        TradingViewRetryJob job = TradingViewRetryJob.builder()
                .id(10)
                .status(TradingViewRetryJobStatus.PENDING)
                .commandId("command")
                .build();

        service().markActivationDead(job, "nickname missing");

        assertThat(job.getStatus()).isEqualTo(TradingViewRetryJobStatus.DEAD);
        assertThat(job.getLastError()).isEqualTo("nickname missing");
        verify(repository).save(job);
    }

    private TradingViewRetryService service() {
        return new TradingViewRetryService(
                repository, userDataService, restTemplate,
                new ObjectMapper().findAndRegisterModules()
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
    }
}
