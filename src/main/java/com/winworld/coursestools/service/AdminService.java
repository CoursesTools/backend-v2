package com.winworld.coursestools.service;

import com.winworld.coursestools.dto.admin.StatisticsAggregation;
import com.winworld.coursestools.dto.admin.StatisticsReadDto;
import com.winworld.coursestools.dto.subscription.PlanSubscriptionCount;
import com.winworld.coursestools.enums.Plan;
import com.winworld.coursestools.enums.TransactionType;
import com.winworld.coursestools.service.user.UserTransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {
    private final UserTransactionService userTransactionService;
    private final SubscriptionService subscriptionService;

    public StatisticsReadDto getStatistics(LocalDate start, LocalDate end) {
        var startPlanData = subscriptionService.getActiveUsersCountOnDateWithPlan(start)
                .stream()
                .collect(Collectors.toMap(
                        PlanSubscriptionCount::getPlan,
                        PlanSubscriptionCount::getCount
                ));

        var endPlanData = subscriptionService.getActiveUsersCountOnDateWithPlan(end)
                .stream()
                .collect(Collectors.toMap(
                        PlanSubscriptionCount::getPlan,
                        PlanSubscriptionCount::getCount
                ));

        Map<Plan, StatisticsAggregation> planDistribution = new HashMap<>();
        for (Plan plan : Plan.values()) {
            Integer startCount = startPlanData.getOrDefault(plan, 0);
            Integer endCount = endPlanData.getOrDefault(plan, 0);
            planDistribution.put(plan, new StatisticsAggregation(startCount, endCount));
        }

        Integer totalStartUsers = calculateActiveUsers(startPlanData);
        Integer totalEndUsers = calculateActiveUsers(endPlanData);
        var usersAggregation = new StatisticsAggregation(totalStartUsers, totalEndUsers);
        return StatisticsReadDto.builder()
                .activeUsers(usersAggregation)
                .planDistribution(planDistribution)
                .revenue(userTransactionService.getTransactionSumAmount(TransactionType.PURCHASE, start, end))
                .payout(userTransactionService.getTransactionSumAmount(TransactionType.WITHDRAWAL, start, end))
                .build();
    }

    private Integer calculateActiveUsers(Map<Plan, Integer> planData) {
        return planData.entrySet().stream()
                .filter(plan -> !plan.getKey().equals(Plan.TRIAL))
                .map(Map.Entry::getValue)
                .reduce(0, Integer::sum);
    }
}
