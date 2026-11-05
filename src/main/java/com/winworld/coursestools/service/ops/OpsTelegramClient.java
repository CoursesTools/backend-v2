package com.winworld.coursestools.service.ops;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winworld.coursestools.config.props.OpsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;

/**
 * Sends ops alerts through the dedicated admin Telegram bot, directly from the backend host
 * (outside the RU network, so Telegram is reachable). The bot token is part of every request URL,
 * so every error text that could reach logs or the database goes through {@link #redact(String)}.
 */
@Component
@Slf4j
public class OpsTelegramClient {
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration MAX_RETRY_AFTER = Duration.ofSeconds(30);

    public enum Result { SENT, UNREACHABLE, FAILED, NOT_CONFIGURED }

    public record SendResult(Result result, String detail) {
    }

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String botToken;
    private final String apiBaseUrl;

    public OpsTelegramClient(OpsProperties opsProperties, ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        requestFactory.setReadTimeout((int) Duration.ofSeconds(20).toMillis());
        this.restTemplate = new RestTemplate(requestFactory);
        this.objectMapper = objectMapper;
        this.botToken = opsProperties.telegram().botToken();
        this.apiBaseUrl = opsProperties.telegram().apiBaseUrl();
    }

    public boolean isConfigured() {
        return StringUtils.hasText(botToken);
    }

    public SendResult sendHtml(long chatId, String html) {
        if (!isConfigured()) {
            return new SendResult(Result.NOT_CONFIGURED, "OPS_TELEGRAM_BOT_TOKEN is not set");
        }
        String url = apiBaseUrl + "/bot" + botToken + "/sendMessage";
        Map<String, Object> body = Map.of(
                "chat_id", chatId,
                "text", html,
                "parse_mode", "HTML",
                "disable_web_page_preview", true);
        String lastError = "unknown error";
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                restTemplate.postForEntity(url, body, String.class);
                return new SendResult(Result.SENT, null);
            } catch (HttpClientErrorException e) {
                String description = telegramDescription(e.getResponseBodyAsString());
                if (e.getStatusCode().value() == 429 && attempt < MAX_ATTEMPTS) {
                    lastError = "rate limited: " + description;
                    sleep(retryAfter(e.getResponseBodyAsString()));
                    continue;
                }
                if (e.getStatusCode().value() == 400 || e.getStatusCode().value() == 403) {
                    // Chat not found / bot blocked: the recipient has to press Start in the bot.
                    return new SendResult(Result.UNREACHABLE, redact(description));
                }
                return new SendResult(Result.FAILED, redact(e.getStatusCode().value() + " " + description));
            } catch (HttpServerErrorException | ResourceAccessException e) {
                lastError = redact(e.getMessage());
                if (attempt < MAX_ATTEMPTS) {
                    sleep(Duration.ofSeconds(attempt * 2L));
                }
            } catch (RestClientException e) {
                return new SendResult(Result.FAILED, redact(e.getMessage()));
            }
        }
        log.warn("Ops Telegram message to {} failed after {} attempts: {}", chatId, MAX_ATTEMPTS, lastError);
        return new SendResult(Result.FAILED, lastError);
    }

    /** Removes the bot token from any text (exception messages include the request URL). */
    String redact(String text) {
        if (text == null || !StringUtils.hasText(botToken)) {
            return text;
        }
        return text.replace(botToken, "<redacted>");
    }

    private String telegramDescription(String responseBody) {
        try {
            JsonNode node = objectMapper.readTree(responseBody);
            return node.path("description").asText(responseBody);
        } catch (Exception e) {
            return responseBody;
        }
    }

    private Duration retryAfter(String responseBody) {
        try {
            long seconds = objectMapper.readTree(responseBody).path("parameters").path("retry_after").asLong(1);
            Duration wait = Duration.ofSeconds(Math.max(1, seconds));
            return wait.compareTo(MAX_RETRY_AFTER) > 0 ? MAX_RETRY_AFTER : wait;
        } catch (Exception e) {
            return Duration.ofSeconds(1);
        }
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
