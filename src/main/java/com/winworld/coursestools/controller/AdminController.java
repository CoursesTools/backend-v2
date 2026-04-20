package com.winworld.coursestools.controller;

import com.winworld.coursestools.dto.PageDto;
import com.winworld.coursestools.dto.admin.AdminOrderFilterDto;
import com.winworld.coursestools.dto.admin.AdminOrderReadDto;
import com.winworld.coursestools.dto.admin.AdminUserReadDto;
import com.winworld.coursestools.dto.admin.ClassicGrantDto;
import com.winworld.coursestools.dto.admin.CreateCustomInvoiceDto;
import com.winworld.coursestools.dto.admin.CustomAccessUpdateDto;
import com.winworld.coursestools.dto.admin.StatisticsReadDto;
import com.winworld.coursestools.dto.admin.UpdatePartnershipCashbackDto;
import com.winworld.coursestools.dto.admin.TradingViewRetryJobFilterDto;
import com.winworld.coursestools.dto.admin.TradingViewRetryJobReadDto;
import com.winworld.coursestools.dto.user.UserSubscriptionReadDto;
import com.winworld.coursestools.enums.Plan;
import com.winworld.coursestools.enums.SubscriptionTier;
import com.winworld.coursestools.service.AdminInvoiceService;
import com.winworld.coursestools.service.AdminOrderService;
import com.winworld.coursestools.service.AdminService;
import com.winworld.coursestools.service.AdminTradingViewRetryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    private final AdminTradingViewRetryService adminTradingViewRetryService;

    @GetMapping("/statistics")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PARTNER')")
    public StatisticsReadDto getStatistics(@RequestParam LocalDate start, @RequestParam LocalDate end) {
        return adminService.getStatistics(start, end);
    }

    @GetMapping("/statistics/plans-by-tier")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<SubscriptionTier, Map<Plan, Integer>> getPlansByTier(
            @RequestParam(defaultValue = "false") boolean grantedOnly
    ) {
        return adminService.getActiveSubscriptionsByTierAndPlan(grantedOnly);
    }

    @GetMapping("/statistics/plans-purchased")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<SubscriptionTier, Map<Plan, Integer>> getPlansPurchased(
            @RequestParam LocalDate start,
            @RequestParam LocalDate end
    ) {
        return adminService.getPurchasedPlansByTier(start, end);
    }

    @PostMapping("/access/classic")
    @PreAuthorize("hasRole('ADMIN')")
    public UserSubscriptionReadDto grantClassicAccess(@RequestBody @Valid ClassicGrantDto dto) {
        return adminService.grantClassicAccess(dto);
    }

    @PostMapping("/access/custom")
    @PreAuthorize("hasRole('ADMIN')")
    public UserSubscriptionReadDto updateCustomAccess(@RequestBody @Valid CustomAccessUpdateDto dto) {
        return adminService.updateCustomAccess(dto);
    }

    @PatchMapping("/users/partnership/cashback")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminUserReadDto updatePartnershipCashback(@RequestBody @Valid UpdatePartnershipCashbackDto dto) {
        return adminService.updatePartnershipCashback(dto);
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminUserReadDto getUserInfo(
            @RequestParam(required = false) Integer userId,
            @RequestParam(required = false) String tradingViewName,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String partnerCode
    ) {
        return adminService.getUserInfo(tradingViewName, email, userId, partnerCode);
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

    @GetMapping("/tv-retry/jobs")
    @PreAuthorize("hasRole('ADMIN')")
    public PageDto<TradingViewRetryJobReadDto> listTvRetryJobs(
            @ParameterObject TradingViewRetryJobFilterDto filter,
            @ParameterObject Pageable pageable
    ) {
        return adminTradingViewRetryService.list(filter, pageable);
    }

    @GetMapping("/tv-retry/jobs/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public TradingViewRetryJobReadDto getTvRetryJob(@PathVariable Integer id) {
        return adminTradingViewRetryService.get(id);
    }

    @PostMapping("/tv-retry/jobs/{id}/retry")
    @PreAuthorize("hasRole('ADMIN')")
    public TradingViewRetryJobReadDto forceRetryTvJob(@PathVariable Integer id) {
        return adminTradingViewRetryService.forceRetry(id);
    }

    @DeleteMapping("/tv-retry/jobs/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void dropTvRetryJob(@PathVariable Integer id) {
        adminTradingViewRetryService.drop(id);
    }
}
