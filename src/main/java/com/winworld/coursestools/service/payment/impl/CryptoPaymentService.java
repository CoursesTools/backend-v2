package com.winworld.coursestools.service.payment.impl;

import com.winworld.coursestools.dto.order.ProcessOrderDto;
import com.winworld.coursestools.dto.payment.crypto.CryptoInvoiceCreateDto;
import com.winworld.coursestools.dto.payment.crypto.CryptoInvoiceCreateResponse;
import com.winworld.coursestools.dto.payment.crypto.CryptoRetrieveDto;
import com.winworld.coursestools.entity.Order;
import com.winworld.coursestools.enums.PaymentMethod;
import com.winworld.coursestools.exception.exceptions.PaymentProcessingException;
import com.winworld.coursestools.service.OrderService;
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

    public CryptoPaymentService(OrderService orderService, RestTemplate restTemplate, CryptoJwtTokenUtil cryptoJwtTokenUtil) {
        super(orderService);
        this.restTemplate = restTemplate;
        addRequiredHeadersInterceptor();
        this.cryptoJwtTokenUtil = cryptoJwtTokenUtil;
    }

    @Override
    @Retry(name = "default", fallbackMethod = "handleFallback")
    public String createPaymentLink(int orderId) {
        Order order = orderService.getOrderById(orderId);

        float amountInUsd = getPriceInUsd(order.getTotalPrice());
        var additionalFields = new CryptoInvoiceCreateDto.AdditionalFields(
                Map.of("hours", TIME_TO_PAY)
        );

        CryptoInvoiceCreateDto dto = CryptoInvoiceCreateDto.builder()
                .amount(amountInUsd)
                .email(order.getUser().getEmail())
                .shopId(shopId)
                .orderId(order.getId().toString())
                .additionalFields(additionalFields)
                .build();

        var response = restTemplate.postForObject(
                INVOICE_CREATE_URL, dto, CryptoInvoiceCreateResponse.class
        );

        return response.getResult().getLink();
    }

    @Override
    public PaymentMethod getPaymentMethod() {
        return PaymentMethod.CRYPTO;
    }

    @Override
    public void processPayment(CryptoRetrieveDto paymentRequest) {
        validatePaymentSignature(paymentRequest);
        Map<String, Object> paymentData = Map.of(INVOICE_ID, paymentRequest.getInvoiceId());
        int orderId = Integer.parseInt(paymentRequest.getOrderId());

        ProcessOrderDto dto = ProcessOrderDto.builder()
                .paymentProviderData(paymentData)
                .orderId(orderId)
                .build();
        verifyPaymentMethodCompatibility(dto.getOrderId());
        orderService.processSuccessfulPayment(dto);
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

    private String handleFallback(int orderId, Throwable throwable) {
        log.error(
                "Error while creating CryptoCloud invoice for order: {}",
                orderId,
                throwable
        );
        throw new PaymentProcessingException("Payment link creation failed");
    }
}
