package com.winworld.coursestools.controller;

import com.winworld.coursestools.dto.admin.AdminUserReadDto;
import com.winworld.coursestools.dto.admin.ChangeUserAccessDto;
import com.winworld.coursestools.dto.admin.StatisticsReadDto;
import com.winworld.coursestools.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/v1/admin")
@RequiredArgsConstructor
public class AdminController {
    private final AdminService adminService;

    @GetMapping("/statistics")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PARTNER')")
    public StatisticsReadDto getStatistics(@RequestParam LocalDate start, @RequestParam LocalDate end) {
        return adminService.getStatistics(start, end);
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
}
