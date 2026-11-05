package com.winworld.coursestools.scheduler;

import com.winworld.coursestools.config.props.OpsProperties;
import com.winworld.coursestools.service.ops.OpsAlertService;
import com.winworld.coursestools.service.ops.checks.AlertDeliveryOpsChecks;
import com.winworld.coursestools.service.ops.checks.InfrastructureOpsChecks;
import com.winworld.coursestools.service.ops.checks.OpsDigestService;
import com.winworld.coursestools.service.ops.checks.PaymentOpsChecks;
import com.winworld.coursestools.service.ops.checks.TvAccessOpsChecks;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Runs the ops health checks. Checks call Stripe, RDAP, TLS endpoints and the alert bot, so they run
 * on their own thread: a slow external service must never hold up the subscription and TV-retry jobs
 * that share the scheduler thread.
 */
@Component
@Slf4j
public class OpsScheduler {
    private final OpsProperties opsProperties;
    private final PaymentOpsChecks paymentOpsChecks;
    private final TvAccessOpsChecks tvAccessOpsChecks;
    private final AlertDeliveryOpsChecks alertDeliveryOpsChecks;
    private final InfrastructureOpsChecks infrastructureOpsChecks;
    private final OpsDigestService opsDigestService;
    private final OpsAlertService opsAlertService;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(16), runnable -> new Thread(runnable, "ops-checks"));

    public OpsScheduler(OpsProperties opsProperties, PaymentOpsChecks paymentOpsChecks,
                        TvAccessOpsChecks tvAccessOpsChecks, AlertDeliveryOpsChecks alertDeliveryOpsChecks,
                        InfrastructureOpsChecks infrastructureOpsChecks, OpsDigestService opsDigestService,
                        OpsAlertService opsAlertService) {
        this.opsProperties = opsProperties;
        this.paymentOpsChecks = paymentOpsChecks;
        this.tvAccessOpsChecks = tvAccessOpsChecks;
        this.alertDeliveryOpsChecks = alertDeliveryOpsChecks;
        this.infrastructureOpsChecks = infrastructureOpsChecks;
        this.opsDigestService = opsDigestService;
        this.opsAlertService = opsAlertService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        submit("startup notice", infrastructureOpsChecks::announceStartup);
    }

    @Scheduled(cron = "${scheduler.ops.payments}")
    public void payments() {
        submit("payments", paymentOpsChecks::run);
    }

    @Scheduled(cron = "${scheduler.ops.tv-access}")
    public void tvAccess() {
        submit("tv access", tvAccessOpsChecks::run);
    }

    @Scheduled(cron = "${scheduler.ops.alert-delivery}")
    public void alertDelivery() {
        submit("alert delivery", alertDeliveryOpsChecks::run);
    }

    @Scheduled(cron = "${scheduler.ops.log-freshness}")
    public void logFreshness() {
        submit("log freshness", infrastructureOpsChecks::runLogFreshness);
    }

    @Scheduled(cron = "${scheduler.ops.daily-infrastructure}")
    public void dailyInfrastructure() {
        submit("daily infrastructure", infrastructureOpsChecks::runDaily);
    }

    @Scheduled(cron = "${scheduler.ops.digest}", zone = "${scheduler.ops.digest-zone}")
    public void digest() {
        submit("daily digest", opsDigestService::sendDailyDigest);
    }

    /** History retention runs even when checks are disabled. */
    @Scheduled(cron = "${scheduler.ops.retention}")
    public void retention() {
        opsAlertService.purgeExpired();
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    private void submit(String name, Runnable task) {
        if (!opsProperties.checksEnabled()) {
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    task.run();
                } catch (Exception e) {
                    log.error("Ops task '{}' failed: {}", name, e.getMessage(), e);
                }
            });
        } catch (RejectedExecutionException e) {
            log.warn("Ops task '{}' skipped: previous checks are still running", name);
        }
    }
}
