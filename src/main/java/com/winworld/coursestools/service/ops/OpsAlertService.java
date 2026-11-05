package com.winworld.coursestools.service.ops;

import com.winworld.coursestools.config.props.OpsProperties;
import com.winworld.coursestools.dto.ops.OpsAlertPageDto;
import com.winworld.coursestools.dto.ops.OpsAlertReadDto;
import com.winworld.coursestools.dto.ops.TelegramTestResultDto;
import com.winworld.coursestools.entity.ops.OpsAlert;
import com.winworld.coursestools.entity.ops.OpsAlertState;
import com.winworld.coursestools.entity.ops.OpsTelegramRecipient;
import com.winworld.coursestools.enums.OpsAlertCategory;
import com.winworld.coursestools.enums.OpsAlertSeverity;
import com.winworld.coursestools.enums.OpsTelegramStatus;
import com.winworld.coursestools.exception.exceptions.ConflictException;
import com.winworld.coursestools.exception.exceptions.EntityNotFoundException;
import com.winworld.coursestools.repository.ops.OpsAlertRepository;
import com.winworld.coursestools.repository.ops.OpsAlertSearchRepository;
import com.winworld.coursestools.repository.ops.OpsAlertStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/**
 * System (ops) alerts. Every alert is stored for the admin-web history page; the global pause and
 * the per-category switches only decide whether it is also sent to the admins' Telegram.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OpsAlertService {
    public static final int MAX_SEARCH_TERMS = 8;
    public static final int MAX_PAGE_SIZE = 100;
    public static final Duration RETENTION = Duration.ofDays(365);
    private static final Duration TEST_MESSAGE_COOLDOWN = Duration.ofSeconds(10);

    private final OpsAlertRepository alertRepository;
    private final OpsAlertStateRepository stateRepository;
    private final OpsAlertSearchRepository searchRepository;
    private final OpsAlertDeliveryService deliveryService;
    private final SystemSettingsService settingsService;
    private final OpsTelegramClient telegramClient;
    private final AdminAuditService auditService;
    private final OpsProperties opsProperties;
    private final AtomicReference<LocalDateTime> lastTestMessageAt = new AtomicReference<>();

    public OpsAlert raise(OpsAlertCategory category, OpsAlertSeverity severity, String title, String body,
                          String dedupKey) {
        OpsAlert alert = new OpsAlert();
        alert.setCategory(category);
        alert.setSeverity(severity);
        alert.setTitle(title.length() > 200 ? title.substring(0, 200) : title);
        alert.setBody(body);
        alert.setDedupKey(dedupKey);
        alert.setTelegramStatus(initialTelegramStatus(category));
        alert.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        OpsAlert saved = alertRepository.save(alert);
        log.info("Ops alert {} [{} {}] {} (telegram: {})",
                saved.getId(), category, severity, title, saved.getTelegramStatus());
        if (saved.getTelegramStatus() == OpsTelegramStatus.PENDING) {
            deliverAfterCommit(saved.getId());
        }
        return saved;
    }

    /** Raises an alert for a one-off event (a dispute, a DEAD job) only the first time it is seen. */
    public boolean raiseOnce(OpsAlertCategory category, OpsAlertSeverity severity, String title, String body,
                             String dedupKey) {
        if (alertRepository.existsByDedupKey(dedupKey)) {
            return false;
        }
        raise(category, severity, title, body, dedupKey);
        return true;
    }

    /**
     * Records the result of a recurring health check: alerts when it starts failing, reminds while it
     * keeps failing, and sends one "resolved" message when it recovers.
     */
    @Transactional
    public void track(String checkKey, OpsAlertCategory category, OpsAlertSeverity failingSeverity,
                      boolean failing, String title, String failureBody) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        OpsAlertState state = stateRepository.findById(checkKey).orElse(null);
        boolean open = state != null && Boolean.TRUE.equals(state.getOpen());
        if (failing && !open) {
            state = state == null ? newState(checkKey) : state;
            state.setOpen(true);
            state.setOpenedAt(now);
            state.setLastNotifiedAt(now);
            raise(category, failingSeverity, title, failureBody, checkKey);
        } else if (failing && reminderDue(state, now)) {
            state.setLastNotifiedAt(now);
            raise(category, failingSeverity, "Still failing: " + title,
                    failureBody + "\n\nFailing since " + state.getOpenedAt() + " UTC.", checkKey);
        } else if (!failing && open) {
            state.setOpen(false);
            raise(category, OpsAlertSeverity.RESOLVED, "Resolved: " + title,
                    "Back to normal. It had been failing since " + state.getOpenedAt() + " UTC.", checkKey);
        } else {
            return;
        }
        state.setUpdatedAt(now);
        stateRepository.save(state);
    }

    @Transactional(readOnly = true)
    public OpsAlertPageDto search(String query, OpsAlertCategory category, OffsetDateTime from,
                                  OffsetDateTime to, Long beforeId, int limit) {
        List<String> terms = searchTerms(query);
        int pageSize = Math.max(1, Math.min(limit, MAX_PAGE_SIZE));
        OpsAlertSearchRepository.Criteria criteria =
                new OpsAlertSearchRepository.Criteria(terms, category, from, to, beforeId, pageSize + 1);
        List<OpsAlertReadDto> rows = searchRepository.search(criteria);
        boolean hasMore = rows.size() > pageSize;
        List<OpsAlertReadDto> items = hasMore ? rows.subList(0, pageSize) : rows;
        Long nextCursor = hasMore ? items.get(items.size() - 1).getId() : null;
        long total = searchRepository.count(criteria);
        return new OpsAlertPageDto(items, nextCursor, total, terms);
    }

    @Transactional(readOnly = true)
    public OpsAlertReadDto get(long id) {
        return searchRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("System alert not found"));
    }

    /** Sends a test message to every recipient so admins can confirm they pressed Start in the bot. */
    public List<TelegramTestResultDto> sendTestMessage(int actorUserId) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime previous = lastTestMessageAt.get();
        if (previous != null && previous.plus(TEST_MESSAGE_COOLDOWN).isAfter(now)
                || !lastTestMessageAt.compareAndSet(previous, now)) {
            throw new ConflictException("A test message was just sent. Wait a few seconds and try again.");
        }
        auditService.record(actorUserId, "OPS_TELEGRAM_TEST", "ops_telegram_recipients", null, null);
        String html = "✅ <b>Test message from CoursesTools</b>\n"
                + "System alerts will arrive in this chat.\n\n"
                + "<a href=\"" + opsProperties.adminWebUrl() + "/settings\">Settings in admin</a>";
        return settingsService.getRecipients().stream()
                .map(recipient -> testOne(recipient, html))
                .toList();
    }

    @Transactional
    public int purgeExpired() {
        int deleted = alertRepository.deleteAllCreatedBefore(LocalDateTime.now(ZoneOffset.UTC).minus(RETENTION));
        if (deleted > 0) {
            log.info("Purged {} ops alerts older than {} days", deleted, RETENTION.toDays());
        }
        return deleted;
    }

    static List<String> searchTerms(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return Arrays.stream(query.toLowerCase(Locale.ROOT).trim().split("\\s+"))
                .filter(term -> !term.isEmpty())
                .map(term -> term.length() > 64 ? term.substring(0, 64) : term)
                .distinct()
                .limit(MAX_SEARCH_TERMS)
                .toList();
    }

    private TelegramTestResultDto testOne(OpsTelegramRecipient recipient, String html) {
        OpsTelegramClient.SendResult result = telegramClient.sendHtml(recipient.getTelegramId(), html);
        return new TelegramTestResultDto(String.valueOf(recipient.getTelegramId()), recipient.getLabel(),
                result.result().name(), result.detail());
    }

    private OpsTelegramStatus initialTelegramStatus(OpsAlertCategory category) {
        if (settingsService.isOpsAlertsPaused()) {
            return OpsTelegramStatus.PAUSED;
        }
        if (!settingsService.isCategoryEnabled(category)) {
            return OpsTelegramStatus.CATEGORY_DISABLED;
        }
        if (!telegramClient.isConfigured()) {
            return OpsTelegramStatus.NOT_CONFIGURED;
        }
        return OpsTelegramStatus.PENDING;
    }

    private void deliverAfterCommit(long alertId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deliveryService.deliver(alertId);
                }
            });
        } else {
            deliveryService.deliver(alertId);
        }
    }

    private boolean reminderDue(OpsAlertState state, LocalDateTime now) {
        Duration every = opsProperties.reminderInterval() == null ? Duration.ofHours(6) : opsProperties.reminderInterval();
        return state.getLastNotifiedAt() == null || !state.getLastNotifiedAt().plus(every).isAfter(now);
    }

    private static OpsAlertState newState(String checkKey) {
        OpsAlertState state = new OpsAlertState();
        state.setCheckKey(checkKey);
        return state;
    }
}
