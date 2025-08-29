package com.winworld.coursestools.controller;

import com.winworld.coursestools.config.security.UserPrincipal;
import com.winworld.coursestools.dto.subscription.SubscriptionActivateDto;
import com.winworld.coursestools.dto.subscription.SubscriptionReadDto;
import com.winworld.coursestools.dto.user.UserSubscriptionReadDto;
import com.winworld.coursestools.enums.SubscriptionName;
import com.winworld.coursestools.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @PostMapping("/start-trial")
    public UserSubscriptionReadDto startTrial(@AuthenticationPrincipal UserPrincipal principal) {
        return subscriptionService.activateCtProTrialForUser(principal.userId());
    }

    @GetMapping("/{name}")
    public SubscriptionReadDto getSubscription(@PathVariable String name) {
        return subscriptionService.getSubscription(SubscriptionName.fromString(name));
    }

    @PostMapping("/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public UserSubscriptionReadDto activateSubscription(@RequestBody SubscriptionActivateDto dto) {
        return subscriptionService.activateSubscription(dto);
    }
}
