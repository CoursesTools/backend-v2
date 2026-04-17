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
        job.setForceRetryCount(0);
        job.setNextAttemptAt(now);
        job.setFirstEnqueuedAt(now);
        job.setLastError(errorMsg);
        repository.save(job);
    }

    /**
     * Keep a pending ACTIVATE job's payload fresh when a user changes their
     * TradingView nickname. Called inside the caller's transaction — if the
     * rename rolls back, this patch rolls back with it.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void onUserTradingViewNameChanged(Integer userId, String newTradingViewName) {
        var existing = repository.findByUserIdAndTypeAndStatus(
                userId, TradingViewRetryJobType.ACTIVATE, TradingViewRetryJobStatus.PENDING);
        if (existing.isEmpty()) return;
        var job = existing.get();
        try {
            var dto = objectMapper.readValue(job.getPayload(), ActivateTradingViewAccessDto.class);
            if (newTradingViewName.equals(dto.getTradingViewName())) return;
            String prior = dto.getTradingViewName();
            dto.setTradingViewName(newTradingViewName);
            job.setPayload(objectMapper.writeValueAsString(dto));
            repository.save(job);
            log.info("Patched PENDING ACTIVATE retry for TV rename: jobId={} userId={} {} -> {}",
                    job.getId(), userId, prior, newTradingViewName);
        } catch (JsonProcessingException e) {
            log.error("Failed to patch ACTIVATE retry payload for userId={}", userId, e);
        }
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

    /**
     * Admin "Retry now" handler. Atomic UPDATE (no read-modify-write race with
     * the scheduler). Resets the automatic-retry cycle (attempts=0), records the
     * manual action via force_retry_count++, schedules immediate pickup. If the
     * clicked row is DEAD but a fresher PENDING exists for same (user, type),
     * drop the stale DEAD and redirect the click to the PENDING — prevents
     * unique-index collision on (user_id, type) WHERE status='PENDING'.
     */
    @Transactional
    public TradingViewRetryJob forceRetry(Integer id) {
        var job = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Retry job " + id + " not found"));
        Integer targetId = id;
        if (job.getStatus() == TradingViewRetryJobStatus.DEAD) {
            var canonical = repository.findByUserIdAndTypeAndStatus(
                    job.getUser().getId(),
                    job.getType(),
                    TradingViewRetryJobStatus.PENDING);
            if (canonical.isPresent() && !canonical.get().getId().equals(id)) {
                log.info("forceRetry on DEAD jobId={} superseded by PENDING jobId={}; dropping DEAD, retrying PENDING",
                        id, canonical.get().getId());
                repository.delete(job);
                targetId = canonical.get().getId();
            }
        }
        final Integer effectiveId = targetId;
        int affected = repository.forceRetry(
                effectiveId, TradingViewRetryJobStatus.PENDING, LocalDateTime.now());
        if (affected == 0) {
            throw new EntityNotFoundException(
                    "Retry job " + effectiveId + " no longer exists (already processed)");
        }
        return repository.findById(effectiveId).orElseThrow(
                () -> new EntityNotFoundException("Retry job " + effectiveId + " vanished after force-retry"));
    }

    /**
     * Idempotent drop. Single atomic DELETE — no stale-entity exception if the
     * scheduler already deleted this row on a successful retry.
     */
    @Transactional
    public void drop(Integer id) {
        int affected = repository.deleteByIdReturning(id);
        if (affected == 0) {
            throw new EntityNotFoundException("Retry job " + id + " not found");
        }
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() > 2048 ? s.substring(0, 2048) : s;
    }
}
