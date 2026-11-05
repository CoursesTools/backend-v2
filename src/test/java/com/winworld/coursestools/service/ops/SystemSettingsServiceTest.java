package com.winworld.coursestools.service.ops;

import com.winworld.coursestools.config.props.OpsProperties;
import com.winworld.coursestools.entity.ops.SystemSettings;
import com.winworld.coursestools.enums.OpsAlertCategory;
import com.winworld.coursestools.exception.exceptions.ConflictException;
import com.winworld.coursestools.exception.exceptions.DataValidationException;
import com.winworld.coursestools.repository.ops.OpsAlertCategorySettingRepository;
import com.winworld.coursestools.repository.ops.OpsTelegramRecipientRepository;
import com.winworld.coursestools.repository.ops.SystemSettingsRepository;
import com.winworld.coursestools.repository.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemSettingsServiceTest {

    @Mock
    private SystemSettingsRepository settingsRepository;
    @Mock
    private OpsAlertCategorySettingRepository categoryRepository;
    @Mock
    private OpsTelegramRecipientRepository recipientRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AdminAuditService auditService;

    private SystemSettingsService service;

    @BeforeEach
    void setUp() {
        OpsProperties opsProperties = new OpsProperties(true, "https://admin.example", Duration.ofHours(6),
                new OpsProperties.Telegram("", "https://api.telegram.org"), new OpsProperties.AlertBot("", ""),
                new OpsProperties.Loki("", "backend"), "/api/v1/payments/stripe", List.of(), List.of());
        service = new SystemSettingsService(settingsRepository, categoryRepository, recipientRepository,
                userRepository, auditService, opsProperties);
    }

    private static SystemSettings settings(int graceDays, int version) {
        SystemSettings settings = new SystemSettings();
        settings.setId(SystemSettings.SINGLETON_ID);
        settings.setGracePeriodDays(graceDays);
        settings.setOpsAlertsPaused(false);
        settings.setVersion(version);
        return settings;
    }

    @Test
    void gracePeriodDays_returnsTheStoredValue() {
        when(settingsRepository.findById(SystemSettings.SINGLETON_ID)).thenReturn(Optional.of(settings(10, 0)));

        assertEquals(10, service.getGracePeriodDays());
    }

    @Test
    void gracePeriodDays_fallsBackToSevenWhenTheSettingCannotBeRead() {
        when(settingsRepository.findById(SystemSettings.SINGLETON_ID))
                .thenThrow(new DataAccessResourceFailureException("db down"));

        assertEquals(7, service.getGracePeriodDays());
    }

    @Test
    void gracePeriodDays_fallsBackToSevenForMissingOrOutOfRangeValues() {
        when(settingsRepository.findById(SystemSettings.SINGLETON_ID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(settings(0, 0)))
                .thenReturn(Optional.of(settings(31, 0)));

        assertEquals(7, service.getGracePeriodDays());
        assertEquals(7, service.getGracePeriodDays());
        assertEquals(7, service.getGracePeriodDays());
    }

    @Test
    void pauseAndCategoryReads_failOpenSoAdminsStillGetAlerts() {
        when(settingsRepository.findById(SystemSettings.SINGLETON_ID))
                .thenThrow(new DataAccessResourceFailureException("db down"));
        when(categoryRepository.findById(OpsAlertCategory.PAYMENTS))
                .thenThrow(new DataAccessResourceFailureException("db down"));

        assertFalse(service.isOpsAlertsPaused());
        assertTrue(service.isCategoryEnabled(OpsAlertCategory.PAYMENTS));
    }

    @Test
    void updateGracePeriod_rejectsAStaleVersion() {
        when(settingsRepository.findById(SystemSettings.SINGLETON_ID)).thenReturn(Optional.of(settings(7, 4)));

        assertThrows(ConflictException.class, () -> service.updateGracePeriodDays(1, 10, 3));
        verify(settingsRepository, never()).saveAndFlush(any());
        verify(auditService, never()).record(anyInt(), anyString(), anyString(), any(), any());
    }

    @Test
    void updateGracePeriod_rejectsOutOfRangeValuesBeforeTouchingTheDatabase() {
        assertThrows(DataValidationException.class, () -> service.updateGracePeriodDays(1, 0, 0));
        assertThrows(DataValidationException.class, () -> service.updateGracePeriodDays(1, 31, 0));
        verify(settingsRepository, never()).findById(any());
    }

    @Test
    void updateGracePeriod_savesAndAuditsTheChange() {
        SystemSettings current = settings(7, 2);
        when(settingsRepository.findById(SystemSettings.SINGLETON_ID)).thenReturn(Optional.of(current));

        service.updateGracePeriodDays(42, 10, 2);

        assertEquals(10, current.getGracePeriodDays());
        assertEquals(42, current.getUpdatedBy());
        verify(settingsRepository).saveAndFlush(current);
        verify(auditService).record(42, "SETTINGS_GRACE_PERIOD_DAYS", "system_settings",
                Map.of("gracePeriodDays", 7), Map.of("gracePeriodDays", 10));
    }

    @Test
    void addRecipient_validatesTheTelegramId() {
        assertThrows(DataValidationException.class, () -> service.addRecipient(1, "-100123456", null));
        assertThrows(DataValidationException.class, () -> service.addRecipient(1, "12ab45", null));
        assertThrows(DataValidationException.class, () -> service.addRecipient(1, "123", null));
        verify(recipientRepository, never()).save(any());
    }

    @Test
    void addRecipient_rejectsDuplicatesAndTooManyRecipients() {
        when(recipientRepository.count()).thenReturn(1L).thenReturn((long) SystemSettingsService.MAX_TELEGRAM_RECIPIENTS);
        when(recipientRepository.existsByTelegramId(639072017L)).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.addRecipient(1, "639072017", "Owner"));
        assertThrows(ConflictException.class, () -> service.addRecipient(1, "123456789", "Second"));
        verify(recipientRepository, never()).save(any());
    }

    @Test
    void addRecipient_savesAndAudits() {
        when(recipientRepository.count()).thenReturn(1L);
        when(recipientRepository.existsByTelegramId(123456789L)).thenReturn(false);
        when(settingsRepository.findById(SystemSettings.SINGLETON_ID)).thenReturn(Optional.of(settings(7, 0)));

        service.addRecipient(5, "123456789", "  Colleague  ");

        verify(recipientRepository).save(org.mockito.ArgumentMatchers.argThat(recipient ->
                recipient.getTelegramId() == 123456789L && "Colleague".equals(recipient.getLabel())));
        verify(auditService).record(eq(5), eq("OPS_TELEGRAM_RECIPIENT_ADDED"), eq("123456789"), eq(null), any());
    }
}
