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
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
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

    @Test
    void processDueJobsNormalizesLegacyFractionalActivationBeforePosting() {
        TradingViewRetryJob job = TradingViewRetryJob.builder()
                .id(10)
                .type(TradingViewRetryJobType.ACTIVATE)
                .status(TradingViewRetryJobStatus.PENDING)
                .payload("{\"email\":\"user@example.com\",\"tier\":\"ESSENTIALS\","
                        + "\"tv\":\"tv-user\",\"expiration\":\"2026-08-28T15:16:22.987654\","
                        + "\"lifetime\":false}")
                .build();
        when(repository.findDueForUpdate(any(), any(Integer.class))).thenReturn(List.of(job));
        when(restTemplate.postForEntity(any(String.class), any(), eq(Void.class)))
                .thenReturn(ResponseEntity.ok().build());

        service().processDueJobs();

        ArgumentCaptor<ActivateTradingViewAccessDto> dto =
                ArgumentCaptor.forClass(ActivateTradingViewAccessDto.class);
        verify(restTemplate).postForEntity(any(String.class), dto.capture(), eq(Void.class));
        assertThat(dto.getValue().getExpiration())
                .isEqualTo(LocalDateTime.of(2026, 8, 28, 15, 16, 22));
        verify(repository).delete(job);
    }

    private TradingViewRetryService service() {
        TradingViewRetryService service = new TradingViewRetryService(
                repository, userDataService, restTemplate,
                new ObjectMapper().findAndRegisterModules()
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
        ReflectionTestUtils.setField(service, "activatingBotUrl", "http://tv.test/open");
        ReflectionTestUtils.setField(service, "batchSize", 20);
        return service;
    }
}
