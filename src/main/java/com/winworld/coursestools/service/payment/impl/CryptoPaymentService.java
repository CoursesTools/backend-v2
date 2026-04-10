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
    public static final String STATUS_SUCCESS = "success";
    private static final String INVOICE_CREATE_URL = "https://api.cryptocloud.plus/v2/invoice/create";
    private static final String CURRENCY_USD = "USD";
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
                .currency(CURRENCY_USD)
                .email(dto.getEmail())
                .shopId(shopId)
                .orderId(dto.getOrderId().toString())
                .additionalFields(additionalFields)
                .build();

        log.info("Creating CryptoCloud invoice for order {} (amount={} {}, plan='{}')",
                dto.getOrderId(), amountInUsd, CURRENCY_USD, dto.getPlanDisplayName());

        var response = restTemplate.postForObject(
                INVOICE_CREATE_URL, createDto, CryptoInvoiceCreateResponse.class
        );

        if (response == null || response.getResult() == null || response.getResult().getLink() == null) {
            log.error("CryptoCloud invoice/create returned empty result for order {}: {}",
                    dto.getOrderId(), response);
            throw new PaymentProcessingException("CryptoCloud returned empty invoice response");
        }

        log.info("CryptoCloud invoice created for order {}: status={}, link={}",
                dto.getOrderId(), response.getStatus(), response.getResult().getLink());
        return response.getResult().getLink();
    }

    @Override
    public PaymentMethod getPaymentMethod() {
        return PaymentMethod.CRYPTO;
    }

    @Override
    public ProcessPaymentDto processPayment(CryptoRetrieveDto paymentRequest) {
        log.info("Processing CryptoCloud postback: orderId={}, invoiceId={}, status={}",
                paymentRequest.getOrderId(),
                paymentRequest.getInvoiceId(),
                paymentRequest.getStatus());

        validatePaymentSignature(paymentRequest);

        // CryptoCloud may send postbacks with non-success statuses (partial, fail, canceled).
        // Only "success" should grant access. If status is null we accept it for backwards
        // compatibility with form-urlencoded postbacks that may not include the field.
        String status = paymentRequest.getStatus();
        if (status != null && !STATUS_SUCCESS.equalsIgnoreCase(status)) {
            log.warn("Ignoring CryptoCloud postback for order {} with non-success status '{}'",
                    paymentRequest.getOrderId(), status);
            return null;
        }

        Map<String, Object> paymentData = Map.of(INVOICE_ID, paymentRequest.getInvoiceId());
        int orderId = Integer.parseInt(paymentRequest.getOrderId());

        log.info("CryptoCloud postback validated for order {} (invoice {})",
                orderId, paymentRequest.getInvoiceId());

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
                log.error("CryptoCloud postback invoice id mismatch: token claim '{}' vs body '{}'",
                        invoiceId, paymentRequest.getInvoiceId());
                throw new PaymentProcessingException("Invoice ids do not match");
            }
        } catch (ExpiredJwtException e) {
            log.error("Expired JWT token while processing CryptoCloud POSTBACK for order {}",
                    paymentRequest.getOrderId(), e);
            throw new PaymentProcessingException("Expired JWT token");
        } catch (SignatureException e) {
            log.error("Invalid JWT signature while processing CryptoCloud POSTBACK for order {} " +
                            "(check CRYPTO_SECRET matches the project SECRET KEY in CryptoCloud dashboard)",
                    paymentRequest.getOrderId(), e);
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
                "Error while creating CryptoCloud invoice for order {} (amount={}, plan='{}'): {}",
                dto.getOrderId(),
                dto.getTotalPrice(),
                dto.getPlanDisplayName(),
                throwable.getMessage(),
                throwable
        );
        throw new PaymentProcessingException("Payment link creation failed");
    }
}
