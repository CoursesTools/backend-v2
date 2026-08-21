package com.winworld.coursestools.service;

import com.winworld.coursestools.repository.user.UserSubscriptionRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PendingSubscriptionReconciliationServiceTest {
    @Mock
    private UserSubscriptionRepository repository;
    @Mock
    private PendingSubscriptionReconciliationWorker worker;

    @Test
    void oneCandidateFailure_doesNotBlockRemainingRecovery() {
        when(repository.findStuckPendingSubscriptionIds(any())).thenReturn(List.of(11, 12));
        when(worker.reconcile(org.mockito.ArgumentMatchers.eq(11), any(),
                org.mockito.ArgumentMatchers.eq("test")))
                .thenThrow(new IllegalStateException("first candidate failed"));
        when(worker.reconcile(org.mockito.ArgumentMatchers.eq(12), any(),
                org.mockito.ArgumentMatchers.eq("test")))
                .thenReturn(true);

        int restored = service(15).reconcileStuckPendingSubscriptions("test");

        assertThat(restored).isEqualTo(1);
        verify(worker).reconcile(org.mockito.ArgumentMatchers.eq(12), any(),
                org.mockito.ArgumentMatchers.eq("test"));
    }

    @Test
    void nonPositiveConfiguredAge_isClampedAwayFromLiveAsyncWork() {
        when(repository.findStuckPendingSubscriptionIds(any())).thenReturn(List.of());
        LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1).minusSeconds(1);

        service(0).reconcileStuckPendingSubscriptions("test");

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).findStuckPendingSubscriptionIds(cutoff.capture());
        LocalDateTime after = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1).plusSeconds(1);
        assertThat(cutoff.getValue()).isBetween(before, after);
    }

    private PendingSubscriptionReconciliationService service(int minimumAgeMinutes) {
        return new PendingSubscriptionReconciliationService(
                repository,
                worker,
                new SimpleMeterRegistry(),
                minimumAgeMinutes
        );
    }
}
