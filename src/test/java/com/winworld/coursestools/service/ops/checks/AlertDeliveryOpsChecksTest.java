package com.winworld.coursestools.service.ops.checks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winworld.coursestools.config.props.OpsProperties;
import com.winworld.coursestools.enums.OpsAlertCategory;
import com.winworld.coursestools.enums.OpsAlertSeverity;
import com.winworld.coursestools.service.ops.OpsAlertService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AlertDeliveryOpsChecksTest {
    private static final Instant NOW = Instant.parse("2026-11-05T12:00:00Z");

    @Mock
    private OpsAlertService opsAlertService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AlertDeliveryOpsChecks checks() {
        OpsProperties properties = new OpsProperties(true, "https://admin.example", Duration.ofHours(6),
                new OpsProperties.Telegram("", ""), new OpsProperties.AlertBot("https://bot/api/ops/stats", "t"),
                new OpsProperties.Loki("", "backend"), "/api/v1/payments/stripe", List.of(), List.of());
        return new AlertDeliveryOpsChecks(opsAlertService, properties, objectMapper);
    }

    private JsonNode stats(String lastReceived, long requeued, long dropped, long queue) throws Exception {
        return objectMapper.readTree("""
                {"lastAlertReceivedAt": %s, "queueSize": %d,
                 "last15m": {"messagesSent": 40, "sendAttemptsFailed": 6, "chatsRequeued": %d, "alertsDroppedStale": %d}}
                """.formatted(lastReceived == null ? "null" : "\"" + lastReceived + "\"", queue, requeued, dropped));
    }

    @Test
    void healthyBot_reportsNothingFailing() throws Exception {
        checks().evaluate(stats("2026-11-05T11:59:00Z", 0, 0, 3), NOW);

        verify(opsAlertService).track(eq("alerts.no-incoming"), eq(OpsAlertCategory.ALERT_DELIVERY),
                eq(OpsAlertSeverity.CRITICAL), eq(false), anyString(), anyString());
        verify(opsAlertService).track(eq("alerts.delivery-failing"), eq(OpsAlertCategory.ALERT_DELIVERY),
                eq(OpsAlertSeverity.WARNING), eq(false), anyString(), anyString());
    }

    @Test
    void retriedFailuresAloneAreNotAnIncident_butUndeliveredAlertsAre() throws Exception {
        checks().evaluate(stats("2026-11-05T11:59:00Z", 2, 0, 3), NOW);

        verify(opsAlertService).track(eq("alerts.delivery-failing"), eq(OpsAlertCategory.ALERT_DELIVERY),
                eq(OpsAlertSeverity.WARNING), eq(true), anyString(), anyString());
    }

    @Test
    void noIncomingAlertsForHalfAnHour_isCritical() throws Exception {
        checks().evaluate(stats("2026-11-05T11:20:00Z", 0, 0, 0), NOW);

        verify(opsAlertService).track(eq("alerts.no-incoming"), eq(OpsAlertCategory.ALERT_DELIVERY),
                eq(OpsAlertSeverity.CRITICAL), eq(true), anyString(), anyString());
    }
}
