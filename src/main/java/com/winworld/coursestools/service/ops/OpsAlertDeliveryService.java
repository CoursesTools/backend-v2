package com.winworld.coursestools.service.ops;

import com.winworld.coursestools.config.props.OpsProperties;
import com.winworld.coursestools.entity.ops.OpsAlert;
import com.winworld.coursestools.entity.ops.OpsTelegramRecipient;
import com.winworld.coursestools.enums.OpsTelegramStatus;
import com.winworld.coursestools.repository.ops.OpsAlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** Delivers a stored ops alert to every admin recipient and records the outcome on the alert. */
@Service
@RequiredArgsConstructor
@Slf4j
public class OpsAlertDeliveryService {
    // Telegram rejects messages over 4096 characters; the full text always stays in admin-web.
    private static final int MAX_BODY_CHARS = 3000;

    private final OpsAlertRepository alertRepository;
    private final SystemSettingsService settingsService;
    private final OpsTelegramClient telegramClient;
    private final OpsProperties opsProperties;

    @Async
    public void deliver(long alertId) {
        OpsAlert alert = alertRepository.findById(alertId).orElse(null);
        if (alert == null) {
            return;
        }
        // Paused after the alert was raised: drop it (paused alerts are never queued).
        if (settingsService.isOpsAlertsPaused()) {
            finish(alert, OpsTelegramStatus.PAUSED, null);
            return;
        }
        List<OpsTelegramRecipient> recipients = settingsService.getRecipients();
        if (recipients.isEmpty()) {
            finish(alert, OpsTelegramStatus.NO_RECIPIENTS, null);
            return;
        }

        String html = render(alert);
        int sent = 0;
        List<String> problems = new ArrayList<>();
        for (OpsTelegramRecipient recipient : recipients) {
            OpsTelegramClient.SendResult result = telegramClient.sendHtml(recipient.getTelegramId(), html);
            if (result.result() == OpsTelegramClient.Result.SENT) {
                sent++;
            } else {
                problems.add(recipient.getTelegramId() + ": " + result.result() + " " + result.detail());
            }
        }
        OpsTelegramStatus status = sent == recipients.size() ? OpsTelegramStatus.SENT
                : sent > 0 ? OpsTelegramStatus.PARTIAL : OpsTelegramStatus.FAILED;
        finish(alert, status, problems.isEmpty() ? null : String.join("; ", problems));
    }

    String render(OpsAlert alert) {
        String body = alert.getBody();
        if (body.length() > MAX_BODY_CHARS) {
            body = body.substring(0, MAX_BODY_CHARS) + "\n… (truncated, full text in admin)";
        }
        String icon = switch (alert.getSeverity()) {
            case CRITICAL -> "🔴";
            case WARNING -> "🟠";
            case INFO -> "🔵";
            case RESOLVED -> "✅";
        };
        return icon + " <b>" + TelegramHtml.escape(alert.getTitle()) + "</b>\n"
                + "<i>" + alert.getCategory().name() + "</i>\n\n"
                + TelegramHtml.fromMarkdown(body) + "\n\n"
                + "<a href=\"" + opsProperties.adminWebUrl() + "/system-alerts?id=" + alert.getId()
                + "\">Open in admin</a>";
    }

    private void finish(OpsAlert alert, OpsTelegramStatus status, String detail) {
        alert.setTelegramStatus(status);
        alert.setTelegramDetail(detail == null ? null : detail.length() > 500 ? detail.substring(0, 500) : detail);
        alertRepository.save(alert);
        if (status == OpsTelegramStatus.FAILED || status == OpsTelegramStatus.PARTIAL) {
            log.warn("Ops alert {} Telegram delivery {}: {}", alert.getId(), status, detail);
        }
    }
}
