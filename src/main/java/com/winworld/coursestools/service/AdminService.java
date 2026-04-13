package com.winworld.coursestools.service;

import com.winworld.coursestools.dto.admin.AdminUserReadDto;
import com.winworld.coursestools.dto.admin.ChangeUserAccessDto;
import com.winworld.coursestools.dto.admin.StatisticsAggregation;
import com.winworld.coursestools.dto.admin.StatisticsReadDto;
import com.winworld.coursestools.dto.order.TierPlanOrderCount;
import com.winworld.coursestools.dto.subscription.PlanSubscriptionCount;
import com.winworld.coursestools.dto.subscription.TierPlanSubscriptionCount;
import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.enums.Plan;
import com.winworld.coursestools.enums.SubscriptionName;
import com.winworld.coursestools.enums.SubscriptionStatus;
import com.winworld.coursestools.enums.SubscriptionTier;
import com.winworld.coursestools.enums.TransactionType;
import com.winworld.coursestools.repository.user.UserTransactionRepository;
import com.winworld.coursestools.mapper.UserMapper;
import com.winworld.coursestools.service.user.UserDataService;
import com.winworld.coursestools.service.user.UserSubscriptionService;
import com.winworld.coursestools.service.user.UserTransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {
    private final UserTransactionService userTransactionService;
    private final SubscriptionService subscriptionService;
    private final UserDataService userDataService;
    private final UserSubscriptionService userSubscriptionService;
    private final UserMapper userMapper;
    private final UserTransactionRepository userTransactionRepository;

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

    @Transactional
    public void changeUserAccess(ChangeUserAccessDto dto) {
        var user = userDataService.getUserByTradingViewName(dto.getTradingViewName());
        var subscription = subscriptionService.getSubscriptionTypeByName(SubscriptionName.COURSESTOOLS);
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
            subscriptionService.createNewSubscription(user, dto.getIsTrial(), dto.getExpiredAt(), dto.getTier());
        }
    }

    public AdminUserReadDto getUserInfo(String tradingViewName, String email, Integer userId) {
        User user = userDataService.getUserInfo(tradingViewName, email, userId);
        return userMapper.toAdminDto(user);
    }

    public Map<SubscriptionTier, Map<Plan, Integer>> getActiveSubscriptionsByTierAndPlan(boolean grantedOnly) {
        Set<SubscriptionStatus> statuses = grantedOnly
                ? EnumSet.of(SubscriptionStatus.GRANTED)
                : EnumSet.of(SubscriptionStatus.GRANTED, SubscriptionStatus.GRACE_PERIOD);
        Map<SubscriptionTier, Map<Plan, Integer>> result = emptyTierPlanMatrix();
        for (TierPlanSubscriptionCount row : subscriptionService.getActiveSubscriptionsByTierAndPlan(statuses)) {
            if (row.getTier() == null || row.getPlan() == Plan.TRIAL) {
                continue;
            }
            result.get(row.getTier()).put(row.getPlan(), row.getCount());
        }
        return result;
    }

    public Map<SubscriptionTier, Map<Plan, Integer>> getPurchasedPlansByTier(LocalDate start, LocalDate end) {
        Map<SubscriptionTier, Map<Plan, Integer>> result = emptyTierPlanMatrix();
        var rows = userTransactionRepository.countPurchasesByTierAndPlan(
                start.atStartOfDay(),
                end.atTime(java.time.LocalTime.MAX)
        );
        for (TierPlanOrderCount row : rows) {
            if (row.getTier() == null || row.getPlan() == Plan.TRIAL) {
                continue;
            }
            result.get(row.getTier()).put(row.getPlan(), row.getCount());
        }
        return result;
    }

    private Map<SubscriptionTier, Map<Plan, Integer>> emptyTierPlanMatrix() {
        Map<SubscriptionTier, Map<Plan, Integer>> matrix = new EnumMap<>(SubscriptionTier.class);
        for (SubscriptionTier tier : SubscriptionTier.values()) {
            Map<Plan, Integer> planBuckets = new LinkedHashMap<>();
            Arrays.stream(Plan.values())
                    .filter(plan -> plan != Plan.TRIAL)
                    .forEach(plan -> planBuckets.put(plan, 0));
            matrix.put(tier, planBuckets);
        }
        return matrix;
    }
}
