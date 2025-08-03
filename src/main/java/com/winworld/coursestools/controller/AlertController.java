package com.winworld.coursestools.controller;

import com.winworld.coursestools.config.security.UserPrincipal;
import com.winworld.coursestools.dto.CountDto;
import com.winworld.coursestools.dto.PageDto;
import com.winworld.coursestools.dto.alert.AlertCategoriesReadDto;
import com.winworld.coursestools.dto.alert.AlertFilterDto;
import com.winworld.coursestools.dto.alert.AlertReadDto;
import com.winworld.coursestools.dto.alert.AlertSubscribeDto;
import com.winworld.coursestools.dto.alert.AlertSubscriptionCategoriesDto;
import com.winworld.coursestools.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping(value = "/v1/alerts", produces = APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class AlertController {
    private final AlertService alertService;

    @GetMapping
    public PageDto<AlertReadDto> getAlertsByFilter(
            @ParameterObject AlertFilterDto alertFilterDto,
            @ParameterObject Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return alertService.getAlertsByFilter(principal.userId(), alertFilterDto, pageable);
    }

    @GetMapping("/categories")
    public AlertSubscriptionCategoriesDto getAlertSubscriptionCategories(@AuthenticationPrincipal UserPrincipal principal) {
        return alertService.getAlertSubscriptionCategories(principal.userId(), false);
    }

    @GetMapping("/categories/multi")
    public AlertSubscriptionCategoriesDto getAlertSubscriptionCategoriesMulti(@AuthenticationPrincipal UserPrincipal principal) {
        return alertService.getAlertSubscriptionCategories(principal.userId(), true);
    }

    @GetMapping("/me/categories")
    public AlertCategoriesReadDto getUserAlertsCategories(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return alertService.getUserAlertsCategories(principal.userId());
    }

    @GetMapping("/me/subscriptions")
    public PageDto<AlertReadDto> getUserAlerts(
            @AuthenticationPrincipal UserPrincipal principal,
            @ParameterObject AlertFilterDto dto,
            @ParameterObject Pageable pageable
    ) {
        return alertService.getUserAlertsByFilter(principal.userId(), dto, pageable);
    }

    @PostMapping("/me/subscriptions")
    public CountDto subscribeOnAlerts(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody AlertSubscribeDto dto
    ) {
        return alertService.subscribeOnAlerts(principal.userId(), dto);
    }

    @DeleteMapping("/me/subscriptions")
    public List<Integer> unsubscribeOnAlerts(
            @AuthenticationPrincipal UserPrincipal principal, @RequestParam @ParameterObject List<Integer> alertsIds
    ) {
        alertService.unSubscribeOnAlerts(principal.userId(), alertsIds);
        return alertsIds;
    }

    @DeleteMapping("/me/subscriptions/all")
    public void unsubscribeOnAlerts(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        alertService.unSubscribeOnAllAlerts(principal.userId());
    }
}
