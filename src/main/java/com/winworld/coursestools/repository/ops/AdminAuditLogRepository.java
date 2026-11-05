package com.winworld.coursestools.repository.ops;

import com.winworld.coursestools.entity.ops.AdminAuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {
    List<AdminAuditLog> findAllByOrderByIdDesc(Pageable pageable);
}
