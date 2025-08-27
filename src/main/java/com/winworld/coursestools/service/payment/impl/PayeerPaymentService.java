package com.winworld.coursestools.service.payment.impl;

import com.winworld.coursestools.dto.payment.CreatePaymentLinkDto;
import com.winworld.coursestools.dto.payment.ProcessPaymentDto;
import com.winworld.coursestools.dto.payment.payeer.PayeerCreatePaymentDto;
import com.winworld.coursestools.dto.payment.payeer.PayeerRetrieveDto;
import com.winworld.coursestools.enums.PaymentMethod;
import com.winworld.coursestools.service.payment.PaymentService;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Base64;

import static java.lang.String.join;

@Service
public class PayeerPaymentService extends PaymentService<PayeerRetrieveDto> {
    private final static String DESCRIPTION = "CoursesTools Subscription";
    private final static String ENCODED_DESCRIPTION = encodeDescription();
    private final static String USD = "USD";
    private final static String SIGN_DELIMITER = ":";
    private final static String CREATE_PAYMENT_URL = "https://payeer.com/merchant";

    private final RestTemplate restTemplate;

    @Value("${payment-platforms.payeer.secret}")
    private String secret;

    @Value("${payment-platforms.payeer.shop-id}")
    private String shopId;


    public PayeerPaymentService(RestTemplate restTemplate) {
        super();
        this.restTemplate = restTemplate;
        addContentTypeHeaderInterceptor();
    }

    @Override
    public String createPaymentLink(CreatePaymentLinkDto dto) {
        String amountInUsd = getPriceInUsd(dto.getTotalPrice()).toString();

        var createPaymentDto = PayeerCreatePaymentDto.builder()
                .amount(amountInUsd)
                .orderId(dto.getOrderId().toString())
                .description(ENCODED_DESCRIPTION)
                .currency(USD)
                .merchantId(shopId)
                .build();

        String signature = generateSign(createPaymentDto);

        return UriComponentsBuilder.fromHttpUrl(CREATE_PAYMENT_URL)
                .queryParam("m_shop", createPaymentDto.getMerchantId())
                .queryParam("m_orderid", createPaymentDto.getOrderId())
                .queryParam("m_amount", createPaymentDto.getAmount())
                .queryParam("m_curr", createPaymentDto.getCurrency())
                .queryParam("m_desc", createPaymentDto.getDescription())
                .queryParam("m_sign", signature)
                .toUriString();
    }

    @Override
    public PaymentMethod getPaymentMethod() {
        return PaymentMethod.PAYEER;
    }

    @Override
    public ProcessPaymentDto processPayment(PayeerRetrieveDto paymentRequest) {
        var signature = generateSign(paymentRequest);
        if (!paymentRequest.getSignature().equalsIgnoreCase(signature) ||
                !paymentRequest.getStatus().equalsIgnoreCase("success")) {
            throw new IllegalArgumentException("Invalid signature or payment status");
        }
        return ProcessPaymentDto.builder()
                .orderId(Integer.parseInt(paymentRequest.getOrderId()))
                .build();
    }

    private void addContentTypeHeaderInterceptor() {
        ClientHttpRequestInterceptor interceptor = (
                request, body, execution
        ) -> {
            request.getHeaders().setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            return execution.execute(request, body);
        };

        this.restTemplate.getInterceptors().add(interceptor);
    }

    private String generateSign(PayeerCreatePaymentDto dto) {
        String[] args = {
                dto.getMerchantId(),
                dto.getOrderId(),
                dto.getAmount(),
                dto.getCurrency(),
                dto.getDescription(),
                secret
        };
        return DigestUtils.sha256Hex(join(SIGN_DELIMITER, args)).toUpperCase();
    }

    private String generateSign(PayeerRetrieveDto dto) {
        String[] args = {
                dto.getOperationId(),
                dto.getOperationPs(),
                dto.getOperationDate(),
                dto.getOperationPayDate(),
                dto.getMerchantId(),
                dto.getOrderId(),
                dto.getAmount(),
                dto.getCurrency(),
                dto.getDescription(),
                dto.getStatus(),
                secret
        };
        return DigestUtils.sha256Hex(join(SIGN_DELIMITER, args));
    }

    private static String encodeDescription() {
        return Base64.getEncoder().encodeToString(DESCRIPTION.getBytes());
    }
}
