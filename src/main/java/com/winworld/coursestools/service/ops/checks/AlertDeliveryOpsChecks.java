package com.winworld.coursestools.service.ops.checks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winworld.coursestools.config.props.OpsProperties;
import com.winworld.coursestools.enums.OpsAlertCategory;
import com.winworld.coursestools.enums.OpsAlertSeverity;
import com.winworld.coursestools.service.ops.OpsAlertService;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** Alert delivery health, read from the alert bot's token-protected stats endpoint. */
@Component
public class AlertDeliveryOpsChecks {
    private static final Duration NO_INCOMING_AFTER = Duration.ofMinutes(30);
    private static final long QUEUE_BACKLOG = 1000;

    private final OpsAlertService opsAlertService;
    private final OpsProperties opsProperties;
    private final ObjectMapper objectMapper;
    private final RestTemplate client;

    public AlertDeliveryOpsChecks(OpsAlertService opsAlertService, OpsProperties opsProperties,
                                  ObjectMapper objectMapper) {
        this.opsAlertService = opsAlertService;
        this.opsProperties = opsProperties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        requestFactory.setReadTimeout((int) Duration.ofSeconds(15).toMillis());
        this.client = new RestTemplate(requestFactory);
    }

    public void run() {
        OpsChecks.safely("alert delivery", this::check);
    }

    void check() {
        Optional<JsonNode> stats = fetchStats();
        if (stats.isEmpty()) {
            return;
        }
        JsonNode node = stats.get();
        evaluate(node, Instant.now());
    }

    void evaluate(JsonNode stats, Instant now) {
        Instant lastReceived = instant(stats.path("lastAlertReceivedAt"));
        boolean silent = lastReceived == null || lastReceived.isBefore(now.minus(NO_INCOMING_AFTER));
        opsAlertService.track("alerts.no-incoming", OpsAlertCategory.ALERT_DELIVERY, OpsAlertSeverity.CRITICAL,
                silent, "No TradingView alerts received",
                "The alert bot has received no alerts for over " + NO_INCOMING_AFTER.toMinutes()
                        + " minutes (last: " + (lastReceived == null ? "never since start" : lastReceived + "") + "). "
                        + "Check the TradingView watchlist alerts and the alert router.");

        JsonNode window = stats.path("last15m");
        long requeued = window.path("chatsRequeued").asLong();
        long droppedStale = window.path("alertsDroppedStale").asLong();
        long failedAttempts = window.path("sendAttemptsFailed").asLong();
        long sent = window.path("messagesSent").asLong();
        long queue = stats.path("queueSize").asLong();
        boolean failing = requeued > 0 || droppedStale > 0 || queue > QUEUE_BACKLOG;
        opsAlertService.track("alerts.delivery-failing", OpsAlertCategory.ALERT_DELIVERY, OpsAlertSeverity.WARNING,
                failing, "Alert delivery to Telegram is failing",
                "Last 15 minutes: **" + sent + "** messages sent, **" + failedAttempts + "** failed attempts, **"
                        + requeued + "** chats requeued after all retries, **" + droppedStale
                        + "** alerts dropped as too old. Queue: **" + queue + "**.\n\n"
                        + "Usually Telegram is unreachable from the alert server; check the Amsterdam relay.");
    }

    private Optional<JsonNode> fetchStats() {
        String token = opsProperties.alertBot().internalToken();
        if (!StringUtils.hasText(token)) {
            opsAlertService.track("alerts.monitoring-not-configured", OpsAlertCategory.ALERT_DELIVERY,
                    OpsAlertSeverity.WARNING, true, "Alert delivery monitoring is not configured",
                    "Set `ALERT_BOT_INTERNAL_TOKEN` on the backend so it can read the alert bot's stats.");
            return Optional.empty();
        }
        opsAlertService.track("alerts.monitoring-not-configured", OpsAlertCategory.ALERT_DELIVERY,
                OpsAlertSeverity.WARNING, false, "Alert delivery monitoring is not configured", "");
        String problem = null;
        JsonNode stats = null;
        try {
            stats = readStats(token);
        } catch (RestClientException e) {
            problem = "Cannot read the alert bot's stats at `" + opsProperties.alertBot().statsUrl() + "`: `"
                    + e.getMessage() + "`. The bot may be down, so alerts may not be reaching anyone.";
        } catch (Exception e) {
            problem = "The alert bot's stats could not be parsed: `" + e.getMessage() + "`.";
        }
        opsAlertService.track("alerts.bot-unreachable", OpsAlertCategory.ALERT_DELIVERY, OpsAlertSeverity.CRITICAL,
                problem != null, "Alert bot unreachable", problem == null ? "" : problem);
        return Optional.ofNullable(stats);
    }

    /** Stats for the daily digest; empty if monitoring isn't configured or the bot can't be read. */
    public Optional<JsonNode> statsForDigest() {
        String token = opsProperties.alertBot().internalToken();
        if (!StringUtils.hasText(token)) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(readStats(token));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private JsonNode readStats(String token) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", token);
        String body = client.exchange(opsProperties.alertBot().statsUrl(), HttpMethod.GET,
                new HttpEntity<>(headers), String.class).getBody();
        return objectMapper.readTree(body);
    }

    private static Instant instant(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isNumber()) {
            // Jackson's default for java.time.Instant: epoch seconds with a fraction.
            return Instant.ofEpochMilli((long) (node.asDouble() * 1000));
        }
        try {
            return Instant.parse(node.asText());
        } catch (Exception e) {
            return null;
        }
    }
}
