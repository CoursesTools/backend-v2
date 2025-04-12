package com.winworld.coursestools.service.payment.impl;

import com.winworld.coursestools.dto.payment.payeer.PayeerCreatePaymentDto;
import com.winworld.coursestools.dto.payment.payeer.PayeerRetrieveDto;
import com.winworld.coursestools.entity.Order;
import com.winworld.coursestools.enums.PaymentMethod;
import com.winworld.coursestools.service.OrderService;
import com.winworld.coursestools.service.payment.PaymentService;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

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


    public PayeerPaymentService(OrderService orderService, RestTemplate restTemplate) {
        super(orderService);
        this.restTemplate = restTemplate;
        addContentTypeHeaderInterceptor();
    }

    @Override
    public String createPaymentLink(int orderId) {
        Order order = orderService.getOrderById(orderId);
        String amountInUsd = getPriceInUsd(order.getTotalPrice()).toString();

        var dto = PayeerCreatePaymentDto.builder()
                .amount(amountInUsd)
                .orderId(order.getId().toString())
                .description(ENCODED_DESCRIPTION)
                .currency(USD)
                .merchantId(shopId)
                .build();
        dto.setSignature(generateSign(dto));
        return null;
    }

    @Override
    public PaymentMethod getPaymentMethod() {
        return PaymentMethod.PAYEER;
    }

    @Override
    public void processPayment(PayeerRetrieveDto paymentRequest) {

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
        return DigestUtils.sha256Hex(join(SIGN_DELIMITER, args));
    }

    private static String encodeDescription() {
        return Base64.getEncoder().encodeToString(DESCRIPTION.getBytes());
    }
}
