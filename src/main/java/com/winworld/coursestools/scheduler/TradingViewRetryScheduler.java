package com.winworld.coursestools.scheduler;

import com.winworld.coursestools.service.external.TradingViewRetryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradingViewRetryScheduler {

    private final TradingViewRetryService retryService;

    @Scheduled(cron = "${scheduler.tv-retry.poll}")
    public void pollDueJobs() {
        try {
            retryService.processDueJobs();
        } catch (Exception e) {
            log.error("TV retry scheduler tick failed", e);
        }
    }
}
