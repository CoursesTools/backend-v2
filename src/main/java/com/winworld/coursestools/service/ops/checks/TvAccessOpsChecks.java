package com.winworld.coursestools.service.ops.checks;

import com.winworld.coursestools.config.props.OpsProperties;
import com.winworld.coursestools.entity.TradingViewRetryJob;
import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.enums.OpsAlertCategory;
import com.winworld.coursestools.enums.OpsAlertSeverity;
import com.winworld.coursestools.enums.TradingViewRetryJobStatus;
import com.winworld.coursestools.repository.TradingViewRetryJobRepository;
import com.winworld.coursestools.service.ops.OpsAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** TradingView access: jobs that need an admin, retries that are stuck, and whether the bot is up. */
@Component
@RequiredArgsConstructor
public class TvAccessOpsChecks {
    private static final Duration STUCK_AFTER = Duration.ofMinutes(30);
    private static final int CONNECT_TIMEOUT_MS = 5000;

    private final OpsAlertService opsAlertService;
    private final TradingViewRetryJobRepository retryJobRepository;
    private final NamedParameterJdbcTemplate jdbc;
    private final OpsProperties opsProperties;

    @Value("${urls.activating-bot}")
    private String activatingBotUrl;

    public void run() {
        OpsChecks.safely("tv dead jobs", this::checkDeadJobs);
        OpsChecks.safely("tv stuck retries", this::checkStuckRetries);
        OpsChecks.safely("tv bot reachable", this::checkBotReachable);
    }

    void checkDeadJobs() {
        for (TradingViewRetryJob job : retryJobRepository.findAllWithUserByStatus(TradingViewRetryJobStatus.DEAD)) {
            User user = job.getUser();
            String body = "User `" + user.getId() + "` " + user.getEmail()
                    + ", TradingView `" + user.getSocial().getTradingViewName() + "`, job `" + job.getId()
                    + "` (" + job.getType() + ").\n"
                    + "Subscription now: " + subscriptionStatus(user.getId()) + "\n"
                    + "Last error: `" + truncate(job.getLastError()) + "`\n\n"
                    + "Check the subscription is active and paid **before** re-running it: "
                    + "[TV retry queue](" + opsProperties.adminWebUrl() + "/tv-retry)";
            opsAlertService.raiseOnce(OpsAlertCategory.TV_ACCESS, OpsAlertSeverity.CRITICAL,
                    "TradingView access job needs admin action", body,
                    "tv-dead:" + job.getId() + ":" + job.getForceRetryCount());
        }
    }

    void checkStuckRetries() {
        long stuck = retryJobRepository.countByStatusAndFirstEnqueuedAtBefore(TradingViewRetryJobStatus.PENDING,
                LocalDateTime.now(ZoneOffset.UTC).minus(STUCK_AFTER));
        opsAlertService.track("tv.stuck-retries", OpsAlertCategory.TV_ACCESS, OpsAlertSeverity.WARNING,
                stuck > 0, "TradingView access retries stuck",
                "**" + stuck + "** TradingView access jobs have been retrying for more than "
                        + STUCK_AFTER.toMinutes() + " minutes: those customers paid but don't have access yet. "
                        + "[TV retry queue](" + opsProperties.adminWebUrl() + "/tv-retry)");
    }

    /** TCP reachability only; the bot's endpoints change real access, so they are never called here. */
    void checkBotReachable() {
        URI uri = URI.create(activatingBotUrl);
        int port = uri.getPort() > 0 ? uri.getPort() : "https".equals(uri.getScheme()) ? 443 : 80;
        String problem = null;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(uri.getHost(), port), CONNECT_TIMEOUT_MS);
        } catch (IOException e) {
            problem = "Cannot connect to the TradingView access bot at `" + uri.getHost() + ":" + port
                    + "`: `" + e.getMessage() + "`. New purchases and renewals are queued for retry.";
        }
        opsAlertService.track("tv.bot-unreachable", OpsAlertCategory.TV_ACCESS, OpsAlertSeverity.CRITICAL,
                problem != null, "TradingView access bot unreachable", problem == null ? "" : problem);
    }

    private String subscriptionStatus(int userId) {
        List<String> rows = jdbc.queryForList("""
                        SELECT us.status || ' until ' || to_char(us.expired_at AT TIME ZONE 'UTC', 'YYYY-MM-DD HH24:MI')
                               || ' UTC (' || COALESCE(us.payment_method, '?') || ')'
                        FROM users_subscriptions us
                        WHERE us.user_id = :userId AND us.is_trial = FALSE
                        ORDER BY us.updated_at DESC NULLS LAST, us.id DESC
                        LIMIT 1
                        """,
                new MapSqlParameterSource("userId", userId), String.class);
        return rows.isEmpty() ? "none" : "`" + rows.get(0) + "`";
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 300 ? text.substring(0, 300) + "…" : text;
    }
}
