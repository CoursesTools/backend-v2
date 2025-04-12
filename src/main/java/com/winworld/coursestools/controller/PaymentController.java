package com.winworld.coursestools.controller;

import com.winworld.coursestools.config.security.UserPrincipal;
import com.winworld.coursestools.dto.payment.BalanceRetrieveDto;
import com.winworld.coursestools.dto.payment.crypto.CryptoRetrieveDto;
import com.winworld.coursestools.facade.PaymentFacade;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE;

@RestController
@RequestMapping("/v1/payments")
@RequiredArgsConstructor
public class PaymentController {
    private static final String STRIPE_SIGNATURE_HEADER = "Stripe-Signature";

    private final PaymentFacade paymentFacade;

    @PostMapping("/stripe")
    public void retrieveStripePayment(
            @RequestBody String payload,
            @RequestHeader(value = STRIPE_SIGNATURE_HEADER) String sigHeader
    ) {
        paymentFacade.retrieveStripePayment(payload, sigHeader);
    }

    @PostMapping(value = "/crypto", consumes = APPLICATION_FORM_URLENCODED_VALUE)
    public void retrieveCryptoPayment(@Valid CryptoRetrieveDto dto) {
        paymentFacade.retrieveCryptoPayment(dto);
    }

    @PostMapping(value = "/balance")
    public void retrieveBalancePayment(
            @RequestParam(name = "order_id") Integer orderId,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        paymentFacade.retrieveBalancePayment(
                new BalanceRetrieveDto(orderId, userPrincipal.userId())
        );
    }
}
