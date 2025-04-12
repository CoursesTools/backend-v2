package com.winworld.coursestools.dto.transaction;

import com.winworld.coursestools.enums.TransactionType;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TransactionReadDto {
    private BigDecimal amount;
    private TransactionType type;
}
