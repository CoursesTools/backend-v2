package com.winworld.coursestools.service.ops;

import com.winworld.coursestools.config.props.OpsProperties;
import com.winworld.coursestools.dto.ops.SystemSettingsReadDto;
import com.winworld.coursestools.dto.ops.TelegramRecipientReadDto;
import com.winworld.coursestools.entity.ops.OpsAlertCategorySetting;
import com.winworld.coursestools.entity.ops.OpsTelegramRecipient;
import com.winworld.coursestools.entity.ops.SystemSettings;
import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.enums.OpsAlertCategory;
import com.winworld.coursestools.exception.exceptions.ConflictException;
import com.winworld.coursestools.exception.exceptions.DataValidationException;
import com.winworld.coursestools.exception.exceptions.EntityNotFoundException;
import com.winworld.coursestools.repository.ops.OpsAlertCategorySettingRepository;
import com.winworld.coursestools.repository.ops.OpsTelegramRecipientRepository;
import com.winworld.coursestools.repository.ops.SystemSettingsRepository;
import com.winworld.coursestools.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Admin-editable settings. Reads used by business logic never throw: if the stored value cannot be
 * read (or is out of range) they fall back to safe defaults, so a settings problem can never stop
 * the subscription lifecycle or silence alerts.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SystemSettingsService {
    public static final int DEFAULT_GRACE_PERIOD_DAYS = 7;
    public static final int MIN_GRACE_PERIOD_DAYS = 1;
    public static final int MAX_GRACE_PERIOD_DAYS = 30;
    public static final int MAX_TELEGRAM_RECIPIENTS = 20;

    private final SystemSettingsRepository settingsRepository;
    private final OpsAlertCategorySettingRepository categoryRepository;
    private final OpsTelegramRecipientRepository recipientRepository;
    private final UserRepository userRepository;
    private final AdminAuditService auditService;
    private final OpsProperties opsProperties;

    public int getGracePeriodDays() {
        try {
            Integer days = settingsRepository.findById(SystemSettings.SINGLETON_ID)
                    .map(SystemSettings::getGracePeriodDays)
                    .orElse(null);
            if (days == null || days < MIN_GRACE_PERIOD_DAYS || days > MAX_GRACE_PERIOD_DAYS) {
                log.warn("Grace period setting missing or invalid ({}), using default {} days",
                        days, DEFAULT_GRACE_PERIOD_DAYS);
                return DEFAULT_GRACE_PERIOD_DAYS;
            }
            return days;
        } catch (RuntimeException e) {
            log.warn("Could not read grace period setting, using default {} days: {}",
                    DEFAULT_GRACE_PERIOD_DAYS, e.getMessage());
            return DEFAULT_GRACE_PERIOD_DAYS;
        }
    }

    /** Fails open: if the setting cannot be read, alerts keep flowing to admins. */
    public boolean isOpsAlertsPaused() {
        try {
            return settingsRepository.findById(SystemSettings.SINGLETON_ID)
                    .map(SystemSettings::getOpsAlertsPaused)
                    .orElse(false);
        } catch (RuntimeException e) {
            log.warn("Could not read ops alert pause setting, treating as not paused: {}", e.getMessage());
            return false;
        }
    }

    /** Fails open: if the setting cannot be read, the category is treated as enabled. */
    public boolean isCategoryEnabled(OpsAlertCategory category) {
        try {
            return categoryRepository.findById(category)
                    .map(OpsAlertCategorySetting::getEnabled)
                    .orElse(true);
        } catch (RuntimeException e) {
            log.warn("Could not read ops alert category {}, treating as enabled: {}", category, e.getMessage());
            return true;
        }
    }

    public List<OpsTelegramRecipient> getRecipients() {
        try {
            return recipientRepository.findAllByOrderByIdAsc();
        } catch (RuntimeException e) {
            log.warn("Could not read ops Telegram recipients: {}", e.getMessage());
            return List.of();
        }
    }

    @Transactional(readOnly = true)
    public SystemSettingsReadDto getSettingsView() {
        SystemSettings settings = loadSettings();
        Map<OpsAlertCategory, Boolean> enabled = categoryRepository.findAll().stream()
                .collect(Collectors.toMap(OpsAlertCategorySetting::getCategory, OpsAlertCategorySetting::getEnabled));
        List<SystemSettingsReadDto.Category> categories = Arrays.stream(OpsAlertCategory.values())
                .map(category -> new SystemSettingsReadDto.Category(category, enabled.getOrDefault(category, true)))
                .toList();
        List<TelegramRecipientReadDto> recipients = recipientRepository.findAllByOrderByIdAsc().stream()
                .map(recipient -> new TelegramRecipientReadDto(
                        recipient.getId(),
                        String.valueOf(recipient.getTelegramId()),
                        recipient.getLabel(),
                        recipient.getCreatedAt()))
                .toList();
        String updatedByEmail = settings.getUpdatedBy() == null ? null
                : userRepository.findById(settings.getUpdatedBy()).map(User::getEmail).orElse(null);
        return new SystemSettingsReadDto(
                settings.getGracePeriodDays(),
                MIN_GRACE_PERIOD_DAYS,
                MAX_GRACE_PERIOD_DAYS,
                DEFAULT_GRACE_PERIOD_DAYS,
                settings.getOpsAlertsPaused(),
                categories,
                recipients,
                settings.getVersion(),
                settings.getUpdatedAt(),
                updatedByEmail,
                StringUtils.hasText(opsProperties.telegram().botToken()));
    }

    @Transactional
    public SystemSettingsReadDto updateGracePeriodDays(int actorUserId, int days, int expectedVersion) {
        if (days < MIN_GRACE_PERIOD_DAYS || days > MAX_GRACE_PERIOD_DAYS) {
            throw new DataValidationException("Grace period must be between %d and %d days"
                    .formatted(MIN_GRACE_PERIOD_DAYS, MAX_GRACE_PERIOD_DAYS));
        }
        SystemSettings settings = loadSettings();
        if (!settings.getVersion().equals(expectedVersion)) {
            throw new ConflictException("Settings were changed by someone else. Reload the page and try again.");
        }
        int before = settings.getGracePeriodDays();
        settings.setGracePeriodDays(days);
        touch(settings, actorUserId);
        saveWithVersionCheck(settings);
        auditService.record(actorUserId, "SETTINGS_GRACE_PERIOD_DAYS", "system_settings",
                Map.of("gracePeriodDays", before), Map.of("gracePeriodDays", days));
        return getSettingsView();
    }

    @Transactional
    public SystemSettingsReadDto setOpsAlertsPaused(int actorUserId, boolean paused) {
        SystemSettings settings = loadSettings();
        boolean before = settings.getOpsAlertsPaused();
        settings.setOpsAlertsPaused(paused);
        touch(settings, actorUserId);
        saveWithVersionCheck(settings);
        auditService.record(actorUserId, paused ? "OPS_ALERTS_PAUSED" : "OPS_ALERTS_RESUMED", "system_settings",
                Map.of("paused", before), Map.of("paused", paused));
        return getSettingsView();
    }

    @Transactional
    public SystemSettingsReadDto setCategoryEnabled(int actorUserId, OpsAlertCategory category, boolean enabled) {
        OpsAlertCategorySetting setting = categoryRepository.findById(category).orElseGet(() -> {
            OpsAlertCategorySetting created = new OpsAlertCategorySetting();
            created.setCategory(category);
            created.setEnabled(true);
            return created;
        });
        boolean before = setting.getEnabled();
        setting.setEnabled(enabled);
        setting.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        setting.setUpdatedBy(actorUserId);
        categoryRepository.save(setting);
        auditService.record(actorUserId, "OPS_ALERT_CATEGORY", category.name(),
                Map.of("enabled", before), Map.of("enabled", enabled));
        return getSettingsView();
    }

    @Transactional
    public SystemSettingsReadDto addRecipient(int actorUserId, String telegramId, String label) {
        long id = parseTelegramId(telegramId);
        if (recipientRepository.count() >= MAX_TELEGRAM_RECIPIENTS) {
            throw new ConflictException("At most %d recipients are allowed".formatted(MAX_TELEGRAM_RECIPIENTS));
        }
        if (recipientRepository.existsByTelegramId(id)) {
            throw new ConflictException("This Telegram id is already a recipient");
        }
        OpsTelegramRecipient recipient = new OpsTelegramRecipient();
        recipient.setTelegramId(id);
        recipient.setLabel(StringUtils.hasText(label) ? label.trim() : null);
        recipient.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        recipient.setCreatedBy(actorUserId);
        recipientRepository.save(recipient);
        auditService.record(actorUserId, "OPS_TELEGRAM_RECIPIENT_ADDED", String.valueOf(id),
                null, recipientSnapshot(recipient));
        return getSettingsView();
    }

    @Transactional
    public SystemSettingsReadDto removeRecipient(int actorUserId, int recipientId) {
        OpsTelegramRecipient recipient = recipientRepository.findById(recipientId)
                .orElseThrow(() -> new EntityNotFoundException("Recipient not found"));
        recipientRepository.delete(recipient);
        auditService.record(actorUserId, "OPS_TELEGRAM_RECIPIENT_REMOVED", String.valueOf(recipient.getTelegramId()),
                recipientSnapshot(recipient), null);
        return getSettingsView();
    }

    private static long parseTelegramId(String telegramId) {
        if (telegramId == null || !telegramId.matches("^[1-9][0-9]{4,14}$")) {
            throw new DataValidationException("Telegram id must be a numeric user id");
        }
        return Long.parseLong(telegramId);
    }

    private static Map<String, Object> recipientSnapshot(OpsTelegramRecipient recipient) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("telegramId", String.valueOf(recipient.getTelegramId()));
        snapshot.put("label", recipient.getLabel());
        return snapshot;
    }

    private SystemSettings loadSettings() {
        return settingsRepository.findById(SystemSettings.SINGLETON_ID)
                .orElseThrow(() -> new EntityNotFoundException("System settings row is missing"));
    }

    private static void touch(SystemSettings settings, int actorUserId) {
        settings.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        settings.setUpdatedBy(actorUserId);
    }

    private void saveWithVersionCheck(SystemSettings settings) {
        try {
            settingsRepository.saveAndFlush(settings);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ConflictException("Settings were changed by someone else. Reload the page and try again.");
        }
    }
}
