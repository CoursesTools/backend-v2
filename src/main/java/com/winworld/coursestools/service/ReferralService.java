package com.winworld.coursestools.service;

import com.winworld.coursestools.dto.PageDto;
import com.winworld.coursestools.dto.partnership.LevelEarningDto;
import com.winworld.coursestools.dto.partnership.UserPartnerReadDto;
import com.winworld.coursestools.entity.Referral;
import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.repository.ReferralRepository;
import com.winworld.coursestools.service.user.UserDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReferralService {

    private final UserDataService userDataService;
    private final ReferralRepository referralRepository;

    @Transactional
    public void registerReferral(User referrer, User referred, boolean isActive) {
        Referral referral = Referral.builder()
                .isActive(isActive)
                .referred(referred)
                .referrer(referrer)
                .build();
        referred.setReferred(referral);
        referralRepository.save(referral);
    }

    @Transactional
    public void registerReferral(String partnerCode, User referred, boolean isActive) {
        var referrer = userDataService.getUserByPartnerCode(partnerCode);
        registerReferral(referrer, referred, isActive);
    }

    public List<LevelEarningDto> calculateLevelEarnings(int userId) {
        return referralRepository.calculateLevelEarnings(userId);
    }

    public void addReferralEarning(int referralId, int amount, int cashbackLevel, int transactionId) {
        referralRepository.addReferralEarning(referralId, amount, cashbackLevel, transactionId);
    }

    public int countReferrals(int userId, boolean isActive) {
        return referralRepository.countByReferrerIdAndIsActive(userId, isActive);
    }

    public PageDto<UserPartnerReadDto> findUserPartnersByReferrerId(int userId, Pageable pageable) {
        return PageDto.of(referralRepository.findUserPartnersByReferrerId(userId, pageable));
    }
}
