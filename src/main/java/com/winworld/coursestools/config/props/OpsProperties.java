package com.winworld.coursestools.config.props;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * System (ops) alerting. The Telegram bot token only ever comes from the environment
 * (OPS_TELEGRAM_BOT_TOKEN); it is never stored in the database or returned by any API.
 */
@ConfigurationProperties("ops")
public record OpsProperties(
        boolean checksEnabled,
        String adminWebUrl,
        Duration reminderInterval,
        Telegram telegram,
        AlertBot alertBot,
        Loki loki,
        String stripeWebhookPath,
        List<String> domains,
        List<String> tlsHosts
) {
    public record Telegram(String botToken, String apiBaseUrl) {
    }

    public record AlertBot(String statsUrl, String internalToken) {
    }

    public record Loki(String url, String tenant) {
    }
}
