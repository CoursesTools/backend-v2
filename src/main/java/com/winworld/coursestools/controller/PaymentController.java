package com.winworld.coursestools.controller;

import com.winworld.coursestools.config.security.UserPrincipal;
import com.winworld.coursestools.dto.RedirectDto;
import com.winworld.coursestools.dto.payment.BalanceRetrieveDto;
import com.winworld.coursestools.dto.payment.crypto.CryptoRetrieveDto;
import com.winworld.coursestools.facade.PaymentFacade;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping("/v1/payments")
@RequiredArgsConstructor
@Slf4j
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

    /**
     * CryptoCloud V2 sends postbacks as either application/x-www-form-urlencoded
     * (legacy / default) or application/json. Both are accepted.
     */
    @PostMapping(value = "/crypto", consumes = APPLICATION_FORM_URLENCODED_VALUE)
    public void retrieveCryptoPaymentForm(@Valid CryptoRetrieveDto dto) {
        log.info("Received CryptoCloud postback (form-urlencoded): orderId={}, invoiceId={}, status={}",
                dto.getOrderId(), dto.getInvoiceId(), dto.getStatus());
        paymentFacade.retrieveCryptoPayment(dto);
    }

    @PostMapping(value = "/crypto", consumes = APPLICATION_JSON_VALUE)
    public void retrieveCryptoPaymentJson(@Valid @RequestBody CryptoRetrieveDto dto) {
        log.info("Received CryptoCloud postback (json): orderId={}, invoiceId={}, status={}",
                dto.getOrderId(), dto.getInvoiceId(), dto.getStatus());
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

    @GetMapping("/stripe/panel")
    private RedirectDto getStripePanel(@AuthenticationPrincipal UserPrincipal principal) {
        return new RedirectDto(paymentFacade.getStripePanel(principal.userId()));
    }
}
