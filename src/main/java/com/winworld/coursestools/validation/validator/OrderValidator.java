package com.winworld.coursestools.validation.validator;

import com.winworld.coursestools.dto.order.CreateOrderDto;
import com.winworld.coursestools.entity.PromoCode;
import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.enums.Plan;
import com.winworld.coursestools.exception.ConflictException;
import com.winworld.coursestools.service.PromoCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderValidator {
    private final PromoCodeService promoCodeService;

    public void validateCreateOrder(User user, CreateOrderDto dto) {
        var userSubscription = user.getSubscription();
        if (userSubscription != null && userSubscription.getPlan().equals(Plan.LIFETIME)) {
            throw new ConflictException("You already have a lifetime plan");
        }

        if (dto.getPromoCode() != null) {
            PromoCode promoCode = promoCodeService.getPromoCodeByCode(dto.getPromoCode());
            validatePromoCodeEligibility(promoCode, user);

            User promoCodeOwner = promoCode.getOwner();
            if (user.getId().equals(promoCodeOwner.getId())) {
                throw new ConflictException("Partner code can`t be used by yourself");
            }
            if (user.getId().equals(promoCodeOwner.getReferredId())) {
                throw new ConflictException("You are already listed as a curator for " +
                        "this user and therefore he cannot become your curator");
            }
        }
    }

    public void validatePromoCodeEligibility(PromoCode promoCode, User user) {
        if (!promoCode.isPartnershipCode()) {
            if (promoCodeService.existsByUserIdAndPromoCodeId(user.getId(), promoCode.getId())) {
                throw new ConflictException("Promo code already used by current user");
            }
            return;
        }

        if (user.hasReferrer()) {
            throw new ConflictException("You already have a referrer");
        }
    }
}
