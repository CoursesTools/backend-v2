package com.winworld.coursestools.controller;

import com.winworld.coursestools.dto.admin.StatisticsReadDto;
import com.winworld.coursestools.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
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
    @PreAuthorize("hasRole('ADMIN')")
    public StatisticsReadDto getStatistics(@RequestParam LocalDate start, @RequestParam LocalDate end) {
        return adminService.getStatistics(start, end);
    }
}
