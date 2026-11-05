package com.winworld.coursestools.service.ops;

import com.winworld.coursestools.config.props.OpsProperties;
import com.winworld.coursestools.entity.ops.OpsAlert;
import com.winworld.coursestools.entity.ops.OpsAlertState;
import com.winworld.coursestools.enums.OpsAlertCategory;
import com.winworld.coursestools.enums.OpsAlertSeverity;
import com.winworld.coursestools.enums.OpsTelegramStatus;
import com.winworld.coursestools.exception.exceptions.ConflictException;
import com.winworld.coursestools.repository.ops.OpsAlertRepository;
import com.winworld.coursestools.repository.ops.OpsAlertSearchRepository;
import com.winworld.coursestools.repository.ops.OpsAlertStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpsAlertServiceTest {

    @Mock
    private OpsAlertRepository alertRepository;
    @Mock
    private OpsAlertStateRepository stateRepository;
    @Mock
    private OpsAlertSearchRepository searchRepository;
    @Mock
    private OpsAlertDeliveryService deliveryService;
    @Mock
    private SystemSettingsService settingsService;
    @Mock
    private OpsTelegramClient telegramClient;
    @Mock
    private AdminAuditService auditService;

    private OpsAlertService service;

    @BeforeEach
    void setUp() {
        OpsProperties opsProperties = new OpsProperties(true, "https://admin.example", Duration.ofHours(6),
                new OpsProperties.Telegram("token", "https://api.telegram.org"), new OpsProperties.AlertBot("", ""),
                new OpsProperties.Loki("", "backend"), "/api/v1/payments/stripe", List.of(), List.of());
        service = new OpsAlertService(alertRepository, stateRepository, searchRepository, deliveryService,
                settingsService, telegramClient, auditService, opsProperties);
        lenient().when(alertRepository.save(any(OpsAlert.class))).thenAnswer(invocation -> {
            OpsAlert alert = invocation.getArgument(0);
            alert.setId(99L);
            return alert;
        });
        lenient().when(telegramClient.isConfigured()).thenReturn(true);
        lenient().when(settingsService.isCategoryEnabled(any())).thenReturn(true);
    }

    private OpsAlert savedAlert() {
        ArgumentCaptor<OpsAlert> captor = ArgumentCaptor.forClass(OpsAlert.class);
        verify(alertRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void raise_storesAndDeliversWhenTelegramIsOn() {
        service.raise(OpsAlertCategory.PAYMENTS, OpsAlertSeverity.CRITICAL, "title", "body", null);

        assertEquals(OpsTelegramStatus.PENDING, savedAlert().getTelegramStatus());
        verify(deliveryService).deliver(99L);
    }

    @Test
    void raise_whilePaused_isStillStoredButNeverSent() {
        when(settingsService.isOpsAlertsPaused()).thenReturn(true);

        service.raise(OpsAlertCategory.PAYMENTS, OpsAlertSeverity.CRITICAL, "title", "body", null);

        assertEquals(OpsTelegramStatus.PAUSED, savedAlert().getTelegramStatus());
        verify(deliveryService, never()).deliver(anyLong());
    }

    @Test
    void raise_forADisabledCategory_isStillStoredButNeverSent() {
        when(settingsService.isCategoryEnabled(OpsAlertCategory.DIGEST)).thenReturn(false);

        service.raise(OpsAlertCategory.DIGEST, OpsAlertSeverity.INFO, "title", "body", null);

        assertEquals(OpsTelegramStatus.CATEGORY_DISABLED, savedAlert().getTelegramStatus());
        verify(deliveryService, never()).deliver(anyLong());
    }

    @Test
    void raise_withoutABotToken_isStoredAsNotConfigured() {
        when(telegramClient.isConfigured()).thenReturn(false);

        service.raise(OpsAlertCategory.TV_ACCESS, OpsAlertSeverity.WARNING, "title", "body", null);

        assertEquals(OpsTelegramStatus.NOT_CONFIGURED, savedAlert().getTelegramStatus());
        verify(deliveryService, never()).deliver(anyLong());
    }

    @Test
    void raiseOnce_skipsAnEventThatWasAlreadyAlerted() {
        when(alertRepository.existsByDedupKey("stripe-event:evt_1")).thenReturn(true);

        assertFalse(service.raiseOnce(OpsAlertCategory.PAYMENTS, OpsAlertSeverity.CRITICAL, "t", "b", "stripe-event:evt_1"));

        verify(alertRepository, never()).save(any());
    }

    @Test
    void track_alertsOnceWhenACheckStartsFailing() {
        when(stateRepository.findById("check")).thenReturn(Optional.empty());

        service.track("check", OpsAlertCategory.INFRASTRUCTURE, OpsAlertSeverity.WARNING, true, "Down", "details");

        assertEquals("Down", savedAlert().getTitle());
        verify(stateRepository).save(org.mockito.ArgumentMatchers.argThat(OpsAlertState::getOpen));
    }

    @Test
    void track_staysQuietWhileStillFailingInsideTheReminderInterval() {
        OpsAlertState open = state(true, LocalDateTime.now(ZoneOffset.UTC).minusHours(1));
        when(stateRepository.findById("check")).thenReturn(Optional.of(open));

        service.track("check", OpsAlertCategory.INFRASTRUCTURE, OpsAlertSeverity.WARNING, true, "Down", "details");

        verify(alertRepository, never()).save(any());
    }

    @Test
    void track_remindsAfterTheReminderInterval() {
        OpsAlertState open = state(true, LocalDateTime.now(ZoneOffset.UTC).minusHours(7));
        when(stateRepository.findById("check")).thenReturn(Optional.of(open));

        service.track("check", OpsAlertCategory.INFRASTRUCTURE, OpsAlertSeverity.WARNING, true, "Down", "details");

        assertEquals("Still failing: Down", savedAlert().getTitle());
    }

    @Test
    void track_sendsOneResolvedMessageOnRecovery() {
        OpsAlertState open = state(true, LocalDateTime.now(ZoneOffset.UTC).minusHours(1));
        when(stateRepository.findById("check")).thenReturn(Optional.of(open));

        service.track("check", OpsAlertCategory.INFRASTRUCTURE, OpsAlertSeverity.WARNING, false, "Down", "");

        OpsAlert resolved = savedAlert();
        assertEquals(OpsAlertSeverity.RESOLVED, resolved.getSeverity());
        assertEquals("Resolved: Down", resolved.getTitle());
        assertFalse(open.getOpen());
    }

    @Test
    void track_healthyAndClosed_doesNothing() {
        when(stateRepository.findById("check")).thenReturn(Optional.of(state(false, null)));

        service.track("check", OpsAlertCategory.INFRASTRUCTURE, OpsAlertSeverity.WARNING, false, "Down", "");

        verify(alertRepository, never()).save(any());
        verify(stateRepository, never()).save(any());
    }

    @Test
    void searchTerms_areLowerCasedDistinctAndCapped() {
        assertEquals(List.of("stripe", "webhook"), OpsAlertService.searchTerms("  Stripe   WEBHOOK stripe "));
        assertTrue(OpsAlertService.searchTerms("   ").isEmpty());
        assertEquals(OpsAlertService.MAX_SEARCH_TERMS,
                OpsAlertService.searchTerms("a b c d e f g h i j k").size());
    }

    @Test
    void testMessage_isRateLimited() {
        when(settingsService.getRecipients()).thenReturn(List.of());

        service.sendTestMessage(1);

        assertThrows(ConflictException.class, () -> service.sendTestMessage(1));
        verify(auditService, times(1)).record(1, "OPS_TELEGRAM_TEST", "ops_telegram_recipients", null, null);
    }

    private static OpsAlertState state(boolean open, LocalDateTime lastNotifiedAt) {
        OpsAlertState state = new OpsAlertState();
        state.setCheckKey("check");
        state.setOpen(open);
        state.setOpenedAt(LocalDateTime.now(ZoneOffset.UTC).minusHours(8));
        state.setLastNotifiedAt(lastNotifiedAt);
        return state;
    }
}
