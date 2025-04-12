package com.winworld.coursestools.service;

import com.winworld.coursestools.config.props.PartnershipProps;
import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.event.ReferralActivityEvent;
import com.winworld.coursestools.service.user.UserDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class PartnershipService {
    private static final String REFERRALS_COUNTER_PREFIX = "referrals-counter:";

    private final UserDataService userDataService;
    private final PartnershipProps partnershipProps;
    private final RedisTemplate<String, Object> redisTemplate;

    @Transactional
    public void processReferralActivity(ReferralActivityEvent event) {
        User referrer = userDataService.getUserById(event.getReferralId());
        if (event.isActiveChanged()) {
            recalculateLevelAfterNewReferral(referrer);
        }
        calculateCashbackAfterNewReferral(referrer, event.getAmount());
    }

    public void recalculateLevelAfterNewReferral(User referrer) {
        Integer rank = referrer.getPartnership().getLevel();

        String referrerKey = REFERRALS_COUNTER_PREFIX + referrer.getId();
        Long referralsCount;
        if (!redisTemplate.hasKey(referrerKey)) {
            referralsCount = (long) userDataService.countByReferrerId(referrer.getId());
            redisTemplate.opsForValue().set(referrerKey, referralsCount);
        } else {
            referralsCount = redisTemplate.opsForValue().increment(referrerKey);
        }

        if (rank.equals(partnershipProps.getLevels().size() - 1)) {
            return;
        }
        Integer requiredReferrals = partnershipProps.getLevels().get(rank).getRequiredReferrals();
        if (referralsCount >= requiredReferrals) {
            referrer.getPartnership().levelUp();
        }
    }

    public void calculateCashbackAfterNewReferral(User referrer, BigDecimal amount) {
        PartnershipProps.Level level;
        BigDecimal earn;

        for (int i = 1; i <= 3; i++) {
            if (referrer == null) return;
            level = partnershipProps.getLevels().get(referrer.getPartnership().getLevel());
            earn = amount
                    .multiply(level.getCashback(i))
                    .divide(BigDecimal.valueOf(100), RoundingMode.HALF_UP);
            referrer.getFinance().addBalance(earn);
            referrer.getFinance().addEarn(earn);
            amount = amount.subtract(earn);
            referrer = referrer.getReferrer();
        }
    }

}
