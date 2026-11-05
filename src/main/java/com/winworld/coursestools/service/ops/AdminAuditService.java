package com.winworld.coursestools.service.ops;

import com.winworld.coursestools.dto.ops.AdminAuditEntryDto;
import com.winworld.coursestools.entity.ops.AdminAuditLog;
import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.repository.ops.AdminAuditLogRepository;
import com.winworld.coursestools.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Who changed which admin setting, when, from what to what. */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAuditService {
    private final AdminAuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Transactional
    public void record(int actorUserId, String action, String target,
                       Map<String, Object> before, Map<String, Object> after) {
        AdminAuditLog entry = new AdminAuditLog();
        entry.setActorUserId(actorUserId);
        entry.setAction(action);
        entry.setTarget(target);
        entry.setBeforeValue(before);
        entry.setAfterValue(after);
        entry.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        auditLogRepository.save(entry);
        log.info("Admin audit: user {} {} {}: {} -> {}", actorUserId, action, target, before, after);
    }

    @Transactional(readOnly = true)
    public List<AdminAuditEntryDto> recent(int limit) {
        List<AdminAuditLog> entries = auditLogRepository.findAllByOrderByIdDesc(PageRequest.of(0, limit));
        Map<Integer, String> emails = userRepository.findAllById(
                        entries.stream().map(AdminAuditLog::getActorUserId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(User::getId, User::getEmail, (a, b) -> a));
        return entries.stream()
                .map(entry -> new AdminAuditEntryDto(
                        entry.getId(),
                        entry.getAction(),
                        entry.getTarget(),
                        entry.getBeforeValue(),
                        entry.getAfterValue(),
                        emails.get(entry.getActorUserId()),
                        entry.getCreatedAt()))
                .toList();
    }
}
