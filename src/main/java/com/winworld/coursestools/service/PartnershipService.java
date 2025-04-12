package com.winworld.coursestools.service;

import com.winworld.coursestools.config.props.PartnershipProps;
import com.winworld.coursestools.dto.PageDto;
import com.winworld.coursestools.dto.partnership.UserPartnerReadDto;
import com.winworld.coursestools.dto.partnership.UserPartnershipReadDto;
import com.winworld.coursestools.entity.Referral;
import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.entity.user.UserTransaction;
import com.winworld.coursestools.service.user.UserDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PartnershipService {
    private static final int CASHBACKS_LEVELS_LIMIT = 2;

    private final PartnershipProps partnershipProps;
    private final ReferralService referralService;
    private final UserDataService userDataService;

    @Transactional
    public void recalculateLevelAfterNewReferral(User referrer) {
        Integer rank = referrer.getPartnership().getLevel();
        int referralsCount = referralService.countReferrals(referrer.getId(), true);

        if (rank.equals(partnershipProps.getLevels().size() - 1)) {
            return;
        }
        Integer requiredReferrals = partnershipProps.getLevels().get(rank).getRequiredReferrals();
        if (referralsCount >= requiredReferrals) {
            referrer.getPartnership().levelUp();
        }
    }

    public UserPartnershipReadDto getUserPartnership(int userId) {
        User user = userDataService.getUserById(userId);
        var nextLevel = getNextPartnershipLevel(user.getPartnership().getLevel());
        var currentLevel = getCurrentLevel(user.getPartnership().getLevel());
        return UserPartnershipReadDto.builder()
                .nextLevelName(nextLevel.getName())
                .requiredReferralsForNextLevel(currentLevel.getRequiredReferrals())
                .activeReferralsCount(referralService.countReferrals(userId, true))
                .inactiveReferralsCount(referralService.countReferrals(userId, false))
                .currentLevelName(currentLevel.getName())
                .curatorDiscord(user.getReferred() != null ?
                        user.getReferred().getReferrer().getProfile().getDiscordId() : null)
                .levelEarnings(referralService.calculateLevelEarnings(userId))
                .partnerCode(user.getPartnerCode().getCode())
                .termsAccepted(user.getPartnership().getTermsAccepted())
                .build();
    }



    @Transactional
    public void calculateCashbackAfterNewReferral(Referral referral, BigDecimal amount, UserTransaction transaction) {
        PartnershipProps.Level level;
        BigDecimal earn;
        User referrer = referral.getReferrer();

        for (int i = 1; i <= CASHBACKS_LEVELS_LIMIT; i++) {
            level = partnershipProps.getLevels().get(referrer.getPartnership().getLevel());
            earn = amount
                    .multiply(level.getCashback(i))
                    .divide(BigDecimal.valueOf(100), RoundingMode.HALF_UP);
            referrer.getFinance().addBalance(earn);
            referralService.addReferralEarning(referral.getId(), earn.intValue(), i, transaction.getId());
            amount = amount.subtract(earn);
            if (referrer.getReferred() == null) return;
            referral = referrer.getReferred();
            referrer = referral.getReferrer();
        }
    }

    public PartnershipProps.Level getNextPartnershipLevel(int level) {
        if (level >= partnershipProps.getLevels().size() - 1) {
            return null;
        }
        return partnershipProps.getLevels().get(level + 1);
    }

    public PartnershipProps.Level getCurrentLevel(int level) {
        return partnershipProps.getLevels().get(level);
    }
}
