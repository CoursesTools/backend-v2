package com.winworld.coursestools.dto.payment.crypto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CryptoInvoiceCreateResponse {
    private String status;
    private Result result;

    @Data
    @NoArgsConstructor
    public static class Result {
        private String link;
    }
}
