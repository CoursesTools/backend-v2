package com.winworld.coursestools.service.payment.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.winworld.coursestools.dto.payment.ProcessPaymentDto;
import com.winworld.coursestools.dto.payment.crypto.CryptoRetrieveDto;
import com.winworld.coursestools.exception.exceptions.SecurityException;
import com.winworld.coursestools.util.jwt.impl.CryptoJwtTokenUtil;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CryptoPaymentServiceTest {
    private static final String SECRET = "01234567890123456789012345678901";

    private final CryptoJwtTokenUtil tokenUtil = new CryptoJwtTokenUtil(SECRET);
    private final CryptoPaymentService cryptoPaymentService = new CryptoPaymentService(
            new RestTemplate(),
            tokenUtil
    );

    @Test
    void processPayment_MalformedToken_ThrowsSecurityException() {
        CryptoRetrieveDto dto = cryptoRetrieveDto("test_invoice", "1", "bogus");

        assertThrows(SecurityException.class, () -> cryptoPaymentService.processPayment(dto));
    }

    @Test
    void processPayment_ValidToken_ReturnsPaymentDto() {
        String token = tokenUtil.generateToken(
                "cryptocloud",
                Map.of("id", "invoice_1"),
                Duration.ofMinutes(5)
        );
        CryptoRetrieveDto dto = cryptoRetrieveDto("invoice_1", "42", token);

        ProcessPaymentDto paymentDto = cryptoPaymentService.processPayment(dto);

        assertEquals(42, paymentDto.getOrderId());
        assertEquals("invoice_1", paymentDto.getPaymentProviderData().get(CryptoPaymentService.INVOICE_ID));
    }

    @Test
    void cryptoRetrieveDto_CurrentJsonPostback_AcceptsDocumentedFields() throws Exception {
        String payload = """
                {
                  "status": "success",
                  "invoice_id": "invoice_1",
                  "amount_crypto": 14.9,
                  "currency": "USDT_TRC20",
                  "order_id": "42",
                  "token": "header.payload.signature",
                  "invoice_info": {
                    "uuid": "INV-invoice_1",
                    "invoice_status": "success",
                    "amount_paid_usd": 16.3
                  },
                  "future_field": "ignored"
                }
                """;

        CryptoRetrieveDto dto = new ObjectMapper().readValue(payload, CryptoRetrieveDto.class);

        assertEquals("success", dto.getStatus());
        assertEquals("invoice_1", dto.getInvoiceId());
        assertEquals("42", dto.getOrderId());
        assertEquals(new BigDecimal("14.9"), dto.getAmountCrypto());
        assertEquals("USDT_TRC20", dto.getCurrency());
        assertEquals("INV-invoice_1", dto.getInvoiceInfo().get("uuid"));
        assertEquals("success", dto.getInvoiceInfo().get("invoice_status"));
    }

    private static CryptoRetrieveDto cryptoRetrieveDto(String invoiceId, String orderId, String token) {
        CryptoRetrieveDto dto = new CryptoRetrieveDto();
        dto.setInvoiceId(invoiceId);
        dto.setOrderId(orderId);
        dto.setToken(token);
        dto.setStatus(CryptoPaymentService.STATUS_SUCCESS);
        return dto;
    }
}
