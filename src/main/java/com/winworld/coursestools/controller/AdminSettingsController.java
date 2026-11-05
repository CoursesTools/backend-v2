package com.winworld.coursestools.controller;

import com.winworld.coursestools.config.security.UserPrincipal;
import com.winworld.coursestools.dto.ops.AdminAuditEntryDto;
import com.winworld.coursestools.dto.ops.GracePeriodUpdateDto;
import com.winworld.coursestools.dto.ops.OpsAlertsPauseDto;
import com.winworld.coursestools.dto.ops.OpsCategoryUpdateDto;
import com.winworld.coursestools.dto.ops.SystemSettingsReadDto;
import com.winworld.coursestools.dto.ops.TelegramRecipientCreateDto;
import com.winworld.coursestools.dto.ops.TelegramTestResultDto;
import com.winworld.coursestools.enums.OpsAlertCategory;
import com.winworld.coursestools.service.ops.AdminAuditService;
import com.winworld.coursestools.service.ops.OpsAlertService;
import com.winworld.coursestools.service.ops.SystemSettingsService;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin-only settings: grace period, ops alert delivery (pause, categories) and Telegram recipients.
 * Every endpoint requires the ADMIN role and every change is written to the admin audit log.
 */
@RestController
@RequestMapping("/v1/admin/settings")
@RequiredArgsConstructor
public class AdminSettingsController {
    private static final int MAX_AUDIT_ENTRIES = 200;

    private final SystemSettingsService systemSettingsService;
    private final OpsAlertService opsAlertService;
    private final AdminAuditService adminAuditService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public SystemSettingsReadDto getSettings() {
        return systemSettingsService.getSettingsView();
    }

    @PutMapping("/grace-period")
    @PreAuthorize("hasRole('ADMIN')")
    public SystemSettingsReadDto updateGracePeriod(
            @RequestBody @Valid GracePeriodUpdateDto dto,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal actor
    ) {
        return systemSettingsService.updateGracePeriodDays(actor.userId(), dto.getGracePeriodDays(), dto.getVersion());
    }

    @PutMapping("/ops-alerts/paused")
    @PreAuthorize("hasRole('ADMIN')")
    public SystemSettingsReadDto setOpsAlertsPaused(
            @RequestBody @Valid OpsAlertsPauseDto dto,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal actor
    ) {
        return systemSettingsService.setOpsAlertsPaused(actor.userId(), dto.getPaused());
    }

    @PutMapping("/ops-alerts/categories/{category}")
    @PreAuthorize("hasRole('ADMIN')")
    public SystemSettingsReadDto setCategoryEnabled(
            @PathVariable OpsAlertCategory category,
            @RequestBody @Valid OpsCategoryUpdateDto dto,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal actor
    ) {
        return systemSettingsService.setCategoryEnabled(actor.userId(), category, dto.getEnabled());
    }

    @PostMapping("/telegram-recipients")
    @PreAuthorize("hasRole('ADMIN')")
    public SystemSettingsReadDto addTelegramRecipient(
            @RequestBody @Valid TelegramRecipientCreateDto dto,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal actor
    ) {
        return systemSettingsService.addRecipient(actor.userId(), dto.getTelegramId(), dto.getLabel());
    }

    @DeleteMapping("/telegram-recipients/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public SystemSettingsReadDto removeTelegramRecipient(
            @PathVariable Integer id,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal actor
    ) {
        return systemSettingsService.removeRecipient(actor.userId(), id);
    }

    @PostMapping("/telegram-recipients/test")
    @PreAuthorize("hasRole('ADMIN')")
    public List<TelegramTestResultDto> sendTestMessage(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal actor
    ) {
        return opsAlertService.sendTestMessage(actor.userId());
    }

    @GetMapping("/audit")
    @PreAuthorize("hasRole('ADMIN')")
    public List<AdminAuditEntryDto> getAuditLog(@RequestParam(defaultValue = "50") int limit) {
        return adminAuditService.recent(Math.max(1, Math.min(limit, MAX_AUDIT_ENTRIES)));
    }
}
