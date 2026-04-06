package com.winworld.coursestools.validation.validator;

import com.winworld.coursestools.entity.Alert;
import com.winworld.coursestools.entity.TierIndicatorPermission;
import com.winworld.coursestools.enums.SubscriptionTier;
import com.winworld.coursestools.exception.exceptions.BusinessException;
import com.winworld.coursestools.repository.TierIndicatorPermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AlertValidator {
    private final TierIndicatorPermissionRepository permissionRepository;

    public void validateIndicatorPermissions(SubscriptionTier tier, int subscriptionTypeId, List<Alert> alerts) {
        if (!permissionRepository.existsByTier(tier)) {
            return;
        }

        Set<String> allowedIndicators = getAllowedIndicators(tier, subscriptionTypeId);

        List<String> forbidden = alerts.stream()
                .map(Alert::getIndicator)
                .filter(ind -> !allowedIndicators.contains(ind))
                .distinct()
                .toList();

        if (!forbidden.isEmpty()) {
            throw new BusinessException(
                    "Your plan does not allow alerts for indicators: " + forbidden
                            + ". Allowed indicators: " + allowedIndicators
            );
        }
    }

    public Set<String> getAllowedIndicators(SubscriptionTier tier, int subscriptionTypeId) {
        if (!permissionRepository.existsByTier(tier)) {
            return Set.of();
        }
        return permissionRepository
                .findByTierAndSubscriptionType_Id(tier, subscriptionTypeId)
                .stream()
                .map(TierIndicatorPermission::getIndicator)
                .collect(Collectors.toSet());
    }
}
