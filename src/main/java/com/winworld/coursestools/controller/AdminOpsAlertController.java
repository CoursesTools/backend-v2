package com.winworld.coursestools.controller;

import com.winworld.coursestools.dto.ops.OpsAlertPageDto;
import com.winworld.coursestools.dto.ops.OpsAlertReadDto;
import com.winworld.coursestools.enums.OpsAlertCategory;
import com.winworld.coursestools.exception.exceptions.DataValidationException;
import com.winworld.coursestools.service.ops.OpsAlertService;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

/** Admin-only history of system (ops) alerts, including those not sent to Telegram. */
@RestController
@RequestMapping("/v1/admin/ops-alerts")
@RequiredArgsConstructor
public class AdminOpsAlertController {
    private static final int MAX_QUERY_LENGTH = 200;

    private final OpsAlertService opsAlertService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public OpsAlertPageDto searchOpsAlerts(
            @Parameter(description = "Words to find in title or body (all must match, case-insensitive)")
            @RequestParam(required = false) String q,
            @RequestParam(required = false) OpsAlertCategory category,
            @Parameter(description = "Inclusive, ISO-8601 with offset")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @Parameter(description = "Exclusive, ISO-8601 with offset")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @Parameter(description = "nextCursor from the previous page")
            @RequestParam(required = false) Long beforeId,
            @RequestParam(defaultValue = "50") int limit
    ) {
        if (q != null && q.length() > MAX_QUERY_LENGTH) {
            throw new DataValidationException("Search text is limited to " + MAX_QUERY_LENGTH + " characters");
        }
        if (from != null && to != null && !from.isBefore(to)) {
            throw new DataValidationException("'from' must be before 'to'");
        }
        return opsAlertService.search(q, category, from, to, beforeId, limit);
    }

    /** Target of the "Open in admin" link in every Telegram alert. */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public OpsAlertReadDto getOpsAlert(@PathVariable Long id) {
        return opsAlertService.get(id);
    }
}
