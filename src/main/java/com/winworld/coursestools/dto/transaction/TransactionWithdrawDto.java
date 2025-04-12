package com.winworld.coursestools.dto.transaction;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

import static com.winworld.coursestools.constants.ValidationMessages.NOT_BLANK_MESSAGE;
import static com.winworld.coursestools.constants.ValidationMessages.POSITIVE_MESSAGE;

@Data
public class TransactionWithdrawDto {
    private static final String WALLET_MESSAGE = "Wallet must be between 10 and 64 characters long";
    private static final String MIN_AMOUNT_MESSAGE = "Minimum amount is 2000 cents (20.00 USD)";

    @Size(min = 10, max = 64, message = WALLET_MESSAGE)
    @NotBlank(message = NOT_BLANK_MESSAGE)
    private String wallet;
    @Positive(message = POSITIVE_MESSAGE)
    @Min(value = 2000, message = MIN_AMOUNT_MESSAGE)
    private BigDecimal amount;
}
