package com.winworld.coursestools.service.external;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winworld.coursestools.dto.external.ActivateTradingViewAccessDto;
import com.winworld.coursestools.dto.external.ChangeTradingViewNameDto;
import com.winworld.coursestools.entity.TradingViewRetryJob;
import com.winworld.coursestools.enums.TradingViewRetryJobStatus;
import com.winworld.coursestools.enums.TradingViewRetryJobType;
import com.winworld.coursestools.exception.exceptions.EntityNotFoundException;
import com.winworld.coursestools.repository.TradingViewRetryJobRepository;
import com.winworld.coursestools.service.user.UserDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradingViewRetryService {

    private final TradingViewRetryJobRepository repository;
    private final UserDataService userDataService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${urls.activating-bot}")
    private String activatingBotUrl;

    @Value("${urls.change-tradingview-bot}")
    private String changeTradingViewBotUrl;

    @Value("${tradingview.retry.max-attempts:10}")
    private int maxAttempts;

    @Value("${tradingview.retry.backoff-seconds:60,300,900,3600,21600,86400,86400,86400,86400,86400}")
    private long[] backoffSeconds;

    @Value("${tradingview.retry.batch-size:20}")
    private int batchSize;

    /**
     * Enqueue a retry in the caller's transaction (outbox pattern): if the
     * caller's tx rolls back, the retry row rolls back with it. A pending
     * job for the same (userId, type) is overwritten with fresher payload.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void enqueue(Integer userId, TradingViewRetryJobType type, Object dto, Throwable cause) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize retry payload for userId={} type={}", userId, type, e);
            return;
        }
        String errorMsg = truncate(cause == null ? null : cause.toString());
        LocalDateTime now = LocalDateTime.now();

        var existing = repository.findByUserIdAndTypeAndStatus(userId, type, TradingViewRetryJobStatus.PENDING);
        if (existing.isPresent()) {
            var job = existing.get();
            log.info("TV retry overwriting existing PENDING job: jobId={} userId={} type={} (prior attempts={}, prior lastError={})",
                    job.getId(), userId, type, job.getAttempts(), job.getLastError());
            job.setPayload(payload);
            job.setAttempts(0);
            job.setNextAttemptAt(now);
            job.setLastError(errorMsg);
            repository.save(job);
            return;
        }

        var job = new TradingViewRetryJob();
        job.setUser(userDataService.getUserById(userId));
        job.setType(type);
        job.setStatus(TradingViewRetryJobStatus.PENDING);
        job.setPayload(payload);
        job.setAttempts(0);
        job.setNextAttemptAt(now);
        job.setFirstEnqueuedAt(now);
        job.setLastError(errorMsg);
        repository.save(job);
    }

    /**
     * Called by the scheduler. Uses REQUIRES_NEW so a poisoned batch can't roll
     * back the scheduler's own bookkeeping. Each job is processed, then either
     * deleted (success) or rescheduled (failure) within this same tx.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processDueJobs() {
        List<TradingViewRetryJob> due = repository.findDueForUpdate(LocalDateTime.now(), batchSize);
        for (TradingViewRetryJob job : due) {
            processOne(job);
        }
    }

    private void processOne(TradingViewRetryJob job) {
        try {
            switch (job.getType()) {
                case ACTIVATE -> {
                    var dto = objectMapper.readValue(job.getPayload(), ActivateTradingViewAccessDto.class);
                    restTemplate.postForEntity(activatingBotUrl, dto, Void.class);
                    log.info("TV retry ACTIVATE succeeded: jobId={} tv={} exp={}",
                            job.getId(), dto.getTradingViewName(), dto.getExpiration());
                }
                case RENAME -> {
                    var dto = objectMapper.readValue(job.getPayload(), ChangeTradingViewNameDto.class);
                    restTemplate.postForEntity(changeTradingViewBotUrl, dto, Void.class);
                    log.info("TV retry RENAME succeeded: jobId={} {} -> {}",
                            job.getId(), dto.getOldName(), dto.getNewName());
                }
            }
            repository.delete(job);
        } catch (Exception e) {
            int attempts = job.getAttempts() + 1;
            job.setAttempts(attempts);
            job.setLastError(truncate(e.toString()));
            if (attempts >= maxAttempts) {
                job.setStatus(TradingViewRetryJobStatus.DEAD);
                log.error("TV retry job moved to DEAD after {} attempts: jobId={} type={} userId={}",
                        attempts, job.getId(), job.getType(), job.getUser().getId(), e);
            } else {
                long backoff = backoffSeconds[Math.min(attempts - 1, backoffSeconds.length - 1)];
                job.setNextAttemptAt(LocalDateTime.now().plusSeconds(backoff));
                log.warn("TV retry job failed (attempt {}/{}), next in {}s: jobId={} type={}",
                        attempts, maxAttempts, backoff, job.getId(), job.getType(), e);
            }
            repository.save(job);
        }
    }

    @Transactional
    public TradingViewRetryJob forceRetry(Integer id) {
        var job = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Retry job " + id + " not found"));
        job.setStatus(TradingViewRetryJobStatus.PENDING);
        job.setNextAttemptAt(LocalDateTime.now());
        job.setLastError(null);
        return repository.save(job);
    }

    @Transactional
    public void drop(Integer id) {
        var job = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Retry job " + id + " not found"));
        repository.delete(job);
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() > 2048 ? s.substring(0, 2048) : s;
    }
}
