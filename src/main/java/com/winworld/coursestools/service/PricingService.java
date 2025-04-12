package com.winworld.coursestools.service;

import com.winworld.coursestools.entity.Code;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class PricingService {

    public BigDecimal calculatePrice(Code code, BigDecimal discountMultiplier, BigDecimal subscriptionPrice) {
        var discount = discountMultiplier.multiply(code.getDiscountValue());
        if (code.getDiscountType().isPercentage()) {
            BigDecimal discountAmount = subscriptionPrice
                    .multiply(discount)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.DOWN);
            return subscriptionPrice.subtract(discountAmount);
        }
        else if (code.getDiscountType().isFixed()) {
            return subscriptionPrice.subtract(discount);
        }
        return subscriptionPrice;
    }
}
