package com.winworld.coursestools.service;

import com.winworld.coursestools.config.props.PartnershipProps;
import com.winworld.coursestools.entity.PromoCode;
import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.exception.EntityNotFoundException;
import com.winworld.coursestools.repository.PromoCodeRepository;
import com.winworld.coursestools.util.StringGeneratorUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PromoCodeService {
    private final PromoCodeRepository promoCodeRepository;
    private final StringGeneratorUtil stringGeneratorUtil;
    private final PartnershipProps partnershipProps;

    public void createPartnerCode(User user) {
        PromoCode promoCode = new PromoCode();
        promoCode.setCode(stringGeneratorUtil.generatePartnerCode());
        promoCode.setMonthDiscount(partnershipProps.getDiscount().getMonth());
        promoCode.setYearDiscount(partnershipProps.getDiscount().getYear());
        promoCode.setLifetimeDiscount(partnershipProps.getDiscount().getLifetime());
        promoCode.setOwner(user);
        promoCodeRepository.save(promoCode);
    }

    public boolean existsByUserIdAndPromoCodeId(int userId, int promoCode) {
        return promoCodeRepository.existsByUserIdAndPromoCodeId(userId, promoCode);
    }

    public void addUsedPromoCodeForUser(int userId, int promoCode) {
        promoCodeRepository.addUsedPromoCodeForUser(userId, promoCode);
    }

    public PromoCode getPromoCodeByCode(String code) {
        return promoCodeRepository.findByCode(code)
                .orElseThrow(() -> new EntityNotFoundException("Promo code not found"));
    }
}
