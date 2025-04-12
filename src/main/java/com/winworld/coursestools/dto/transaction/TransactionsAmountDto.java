package com.winworld.coursestools.dto.transaction;

import com.winworld.coursestools.enums.Currency;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionsAmountDto {
    private Float amount;
    private Currency currency;
}
