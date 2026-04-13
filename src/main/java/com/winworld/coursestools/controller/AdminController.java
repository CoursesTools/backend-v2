package com.winworld.coursestools.controller;

import com.winworld.coursestools.dto.PageDto;
import com.winworld.coursestools.dto.admin.AdminOrderFilterDto;
import com.winworld.coursestools.dto.admin.AdminOrderReadDto;
import com.winworld.coursestools.dto.admin.AdminUserReadDto;
import com.winworld.coursestools.dto.admin.ChangeUserAccessDto;
import com.winworld.coursestools.dto.admin.CreateCustomInvoiceDto;
import com.winworld.coursestools.dto.admin.StatisticsReadDto;
import com.winworld.coursestools.enums.Plan;
import com.winworld.coursestools.enums.SubscriptionTier;
import com.winworld.coursestools.service.AdminInvoiceService;
import com.winworld.coursestools.service.AdminOrderService;
import com.winworld.coursestools.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/v1/admin")
@RequiredArgsConstructor
public class AdminController {
    private final AdminService adminService;
    private final AdminInvoiceService adminInvoiceService;
    private final AdminOrderService adminOrderService;

    @GetMapping("/statistics")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PARTNER')")
    public StatisticsReadDto getStatistics(@RequestParam LocalDate start, @RequestParam LocalDate end) {
        return adminService.getStatistics(start, end);
    }

    @GetMapping("/statistics/plans-by-tier")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<SubscriptionTier, Map<Plan, Integer>> getPlansByTier() {
        return adminService.getActiveSubscriptionsByTierAndPlan();
    }

    @PostMapping("/access")
    @PreAuthorize("hasRole('ADMIN')")
    public void changeUserAccess(@RequestBody @Valid ChangeUserAccessDto dto) {
        adminService.changeUserAccess(dto);
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminUserReadDto getUserInfo(
            @RequestParam(required = false) Integer userId,
            @RequestParam(required = false) String tradingViewName,
            @RequestParam(required = false) String email
    ) {
        return adminService.getUserInfo(tradingViewName, email, userId);
    }

    @PostMapping("/invoices/create")
    @PreAuthorize("hasRole('ADMIN')")
    public String createCustomInvoice(@RequestBody @Valid CreateCustomInvoiceDto dto) {
        return adminInvoiceService.createCustomInvoice(dto);
    }

    @GetMapping("/orders")
    @PreAuthorize("hasRole('ADMIN')")
    public PageDto<AdminOrderReadDto> getOrders(
            @ParameterObject AdminOrderFilterDto filter,
            @ParameterObject Pageable pageable
    ) {
        return adminOrderService.getOrders(filter, pageable);
    }
}
