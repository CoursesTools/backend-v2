package com.winworld.coursestools.service;

import com.winworld.coursestools.dto.admin.AdminUserReadDto;
import com.winworld.coursestools.dto.admin.ChangeUserAccessDto;
import com.winworld.coursestools.dto.admin.StatisticsAggregation;
import com.winworld.coursestools.dto.admin.StatisticsReadDto;
import com.winworld.coursestools.dto.subscription.PlanSubscriptionCount;
import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.enums.Plan;
import com.winworld.coursestools.enums.SubscriptionName;
import com.winworld.coursestools.enums.SubscriptionStatus;
import com.winworld.coursestools.enums.TransactionType;
import com.winworld.coursestools.mapper.UserMapper;
import com.winworld.coursestools.service.user.UserDataService;
import com.winworld.coursestools.service.user.UserSubscriptionService;
import com.winworld.coursestools.service.user.UserTransactionService;
import jakarta.validation.Valid;
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
    private final UserDataService userDataService;
    private final UserSubscriptionService userSubscriptionService;
    private final UserMapper userMapper;

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

    public void changeUserAccess(ChangeUserAccessDto dto) {
        var user = userDataService.getUserByTradingViewName(dto.getTradingViewName());
        var subscription = subscriptionService.getSubscriptionTypeByName(SubscriptionName.COURSESTOOLSPRO);
        var userSubscriptionOptional = userSubscriptionService.getUserSubBySubTypeIdNotTerminated(user.getId(), subscription.getId());
        if (userSubscriptionOptional.isPresent()) {
            var userSubscription = userSubscriptionOptional.get();
            if (userSubscription.getStatus() == SubscriptionStatus.GRACE_PERIOD) {
                subscriptionService.updateGracePeriodSubscription(userSubscription, user, dto.getExpiredAt());
            }
            else {
                subscriptionService.extendExistingSubscription(userSubscription, user, dto.getExpiredAt());
            }
        } else {
            subscriptionService.createNewSubscription(user, dto.getIsTrial(), dto.getExpiredAt());
        }
    }

    public AdminUserReadDto getUserInfo(String tradingViewName, String email, Integer userId) {
        User user = userDataService.getUserInfo(tradingViewName, email, userId);
        return userMapper.toAdminDto(user);
    }
}
