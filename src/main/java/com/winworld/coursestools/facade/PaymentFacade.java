package com.winworld.coursestools.facade;

import com.winworld.coursestools.dto.payment.BalanceRetrieveDto;
import com.winworld.coursestools.dto.payment.StripeRetrieveDto;
import com.winworld.coursestools.dto.payment.crypto.CryptoRetrieveDto;
import com.winworld.coursestools.service.payment.impl.BalancePaymentService;
import com.winworld.coursestools.service.payment.impl.CryptoPaymentService;
import com.winworld.coursestools.service.payment.impl.StripePaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentFacade {
    private final StripePaymentService stripePaymentService;
    private final CryptoPaymentService cryptoPaymentService;
    private final BalancePaymentService balancePaymentService;

    public void retrieveStripePayment(String payload, String signature) {
        StripeRetrieveDto dto = StripeRetrieveDto.builder()
                .payload(payload)
                .signature(signature)
                .build();
        stripePaymentService.processPayment(dto);
    }

    public void retrieveCryptoPayment(CryptoRetrieveDto dto) {
        cryptoPaymentService.processPayment(dto);
    }

    public void retrieveBalancePayment(BalanceRetrieveDto dto) {
        balancePaymentService.processPayment(dto);
    }
}
