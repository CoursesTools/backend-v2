package com.winworld.coursestools.dto.transaction;

import com.winworld.coursestools.enums.Currency;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class WithdrawRequestDto {
    private String email;
    private String wallet;
    //In Usd
    private BigDecimal amount;
    private String secret;
    private int transactionId;
    private Currency currency;
}
