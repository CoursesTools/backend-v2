package com.winworld.coursestools.service.ops;

import com.winworld.coursestools.config.props.OpsProperties;
import com.winworld.coursestools.entity.ops.OpsAlert;
import com.winworld.coursestools.entity.ops.OpsTelegramRecipient;
import com.winworld.coursestools.enums.OpsAlertCategory;
import com.winworld.coursestools.enums.OpsAlertSeverity;
import com.winworld.coursestools.enums.OpsTelegramStatus;
import com.winworld.coursestools.repository.ops.OpsAlertRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpsAlertDeliveryServiceTest {

    @Mock
    private OpsAlertRepository alertRepository;
    @Mock
    private SystemSettingsService settingsService;
    @Mock
    private OpsTelegramClient telegramClient;

    private OpsAlertDeliveryService service;
    private OpsAlert alert;

    @BeforeEach
    void setUp() {
        OpsProperties properties = new OpsProperties(true, "https://admin.example", Duration.ofHours(6),
                new OpsProperties.Telegram("token", "https://api.telegram.org"), new OpsProperties.AlertBot("", ""),
                new OpsProperties.Loki("", "backend"), "/api/v1/payments/stripe", List.of(), List.of());
        service = new OpsAlertDeliveryService(alertRepository, settingsService, telegramClient, properties);
        alert = new OpsAlert();
        alert.setId(5L);
        alert.setCategory(OpsAlertCategory.PAYMENTS);
        alert.setSeverity(OpsAlertSeverity.CRITICAL);
        alert.setTitle("Customer <script> title");
        alert.setBody("x".repeat(5000));
        alert.setTelegramStatus(OpsTelegramStatus.PENDING);
    }

    private static OpsTelegramRecipient recipient(long telegramId) {
        OpsTelegramRecipient recipient = new OpsTelegramRecipient();
        recipient.setTelegramId(telegramId);
        return recipient;
    }

    @Test
    void pausingAfterTheAlertWasRaised_dropsItInsteadOfQueueing() {
        when(alertRepository.findById(5L)).thenReturn(Optional.of(alert));
        when(settingsService.isOpsAlertsPaused()).thenReturn(true);

        service.deliver(5L);

        assertEquals(OpsTelegramStatus.PAUSED, alert.getTelegramStatus());
        verify(telegramClient, never()).sendHtml(anyLong(), anyString());
    }

    @Test
    void someRecipientsUnreachable_isRecordedAsPartial() {
        when(alertRepository.findById(5L)).thenReturn(Optional.of(alert));
        when(settingsService.getRecipients()).thenReturn(List.of(recipient(111111), recipient(222222)));
        when(telegramClient.sendHtml(org.mockito.ArgumentMatchers.eq(111111L), anyString()))
                .thenReturn(new OpsTelegramClient.SendResult(OpsTelegramClient.Result.SENT, null));
        when(telegramClient.sendHtml(org.mockito.ArgumentMatchers.eq(222222L), anyString()))
                .thenReturn(new OpsTelegramClient.SendResult(OpsTelegramClient.Result.UNREACHABLE, "chat not found"));

        service.deliver(5L);

        assertEquals(OpsTelegramStatus.PARTIAL, alert.getTelegramStatus());
        assertTrue(alert.getTelegramDetail().contains("222222"));
    }

    @Test
    void renderedMessageEscapesTheTitleAndStaysUnderTelegramsLimit() {
        String html = service.render(alert);

        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("&lt;script&gt;"));
        assertTrue(html.length() < 4096);
        assertTrue(html.contains("https://admin.example/system-alerts?id=5"));
    }
}
