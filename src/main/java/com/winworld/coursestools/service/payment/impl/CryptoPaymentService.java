package com.winworld.coursestools.service.payment.impl;

import com.winworld.coursestools.dto.payment.ProcessPaymentDto;
import com.winworld.coursestools.dto.payment.CreatePaymentLinkDto;
import com.winworld.coursestools.dto.payment.crypto.CryptoInvoiceCreateDto;
import com.winworld.coursestools.dto.payment.crypto.CryptoInvoiceCreateResponse;
import com.winworld.coursestools.dto.payment.crypto.CryptoRetrieveDto;
import com.winworld.coursestools.enums.PaymentMethod;
import com.winworld.coursestools.exception.exceptions.PaymentProcessingException;
import com.winworld.coursestools.service.payment.PaymentService;
import com.winworld.coursestools.util.jwt.impl.CryptoJwtTokenUtil;
import io.github.resilience4j.retry.annotation.Retry;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

@Service
@Slf4j
public class CryptoPaymentService extends PaymentService<CryptoRetrieveDto> {
    public static final String INVOICE_ID = "invoiceId";
    private static final String INVOICE_CREATE_URL = "https://api.cryptocloud.plus/v2/invoice/create";
    private static final Integer TIME_TO_PAY = 24;

    private final RestTemplate restTemplate;
    private final CryptoJwtTokenUtil cryptoJwtTokenUtil;

    @Value("${payment-platforms.crypto.api-key}")
    private String apiKey;

    @Value("${payment-platforms.crypto.shop-id}")
    private String shopId;

    public CryptoPaymentService(RestTemplate restTemplate, CryptoJwtTokenUtil cryptoJwtTokenUtil) {
        super();
        this.restTemplate = restTemplate;
        addRequiredHeadersInterceptor();
        this.cryptoJwtTokenUtil = cryptoJwtTokenUtil;
    }

    @Override
    @Retry(name = "default", fallbackMethod = "handleFallback")
    public String createPaymentLink(CreatePaymentLinkDto dto) {
        float amountInUsd = getPriceInUsd(dto.getTotalPrice());
        var additionalFields = new CryptoInvoiceCreateDto.AdditionalFields(
                Map.of("hours", TIME_TO_PAY)
        );

        CryptoInvoiceCreateDto createDto = CryptoInvoiceCreateDto.builder()
                .amount(amountInUsd)
                .email(dto.getEmail())
                .shopId(shopId)
                .orderId(dto.getOrderId().toString())
                .additionalFields(additionalFields)
                .build();

        var response = restTemplate.postForObject(
                INVOICE_CREATE_URL, createDto, CryptoInvoiceCreateResponse.class
        );

        return response.getResult().getLink();
    }

    @Override
    public PaymentMethod getPaymentMethod() {
        return PaymentMethod.CRYPTO;
    }

    @Override
    public ProcessPaymentDto processPayment(CryptoRetrieveDto paymentRequest) {
        validatePaymentSignature(paymentRequest);
        Map<String, Object> paymentData = Map.of(INVOICE_ID, paymentRequest.getInvoiceId());
        int orderId = Integer.parseInt(paymentRequest.getOrderId());

        return ProcessPaymentDto.builder()
                .paymentProviderData(paymentData)
                .orderId(orderId)
                .build();
    }

    private void validatePaymentSignature(CryptoRetrieveDto paymentRequest) {
        try {
            String invoiceId = cryptoJwtTokenUtil.extractClaim(
                    paymentRequest.getToken(), "id", String.class
            );
            if (!invoiceId.equals(paymentRequest.getInvoiceId())) {
                throw new PaymentProcessingException("Invoice ids do not match");
            }
        } catch (ExpiredJwtException e) {
            log.error("Expired JWT token while process Crypto POSTBACK", e);
            throw new PaymentProcessingException("Expired JWT token");
        } catch (SignatureException e) {
            log.error("Signature not valid while process Crypto POSTBACK", e);
            throw new PaymentProcessingException("Signature not valid");
        }
    }

    private void addRequiredHeadersInterceptor() {
        ClientHttpRequestInterceptor interceptor = (
                request, body, execution
        ) -> {
            request.getHeaders().set(AUTHORIZATION, "Token " + apiKey);
            request.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            return execution.execute(request, body);
        };

        this.restTemplate.getInterceptors().add(interceptor);
    }

    private String handleFallback(CreatePaymentLinkDto dto, Throwable throwable) {
        log.error(
                "Error while creating CryptoCloud invoice for order: {}",
                dto.getOrderId(),
                throwable
        );
        throw new PaymentProcessingException("Payment link creation failed");
    }
}
