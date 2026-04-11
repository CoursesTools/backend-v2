package com.winworld.coursestools.service.payment.impl;

import com.winworld.coursestools.dto.payment.ProcessPaymentDto;
import com.winworld.coursestools.dto.payment.crypto.CryptoRetrieveDto;
import com.winworld.coursestools.exception.exceptions.SecurityException;
import com.winworld.coursestools.util.jwt.impl.CryptoJwtTokenUtil;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

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

    private static CryptoRetrieveDto cryptoRetrieveDto(String invoiceId, String orderId, String token) {
        CryptoRetrieveDto dto = new CryptoRetrieveDto();
        dto.setInvoiceId(invoiceId);
        dto.setOrderId(orderId);
        dto.setToken(token);
        dto.setStatus(CryptoPaymentService.STATUS_SUCCESS);
        return dto;
    }
}
