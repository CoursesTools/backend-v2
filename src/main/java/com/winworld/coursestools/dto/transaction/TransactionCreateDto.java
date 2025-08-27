package com.winworld.coursestools.dto.transaction;

import com.winworld.coursestools.entity.Order;
import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class TransactionCreateDto {
    private User user;
    private BigDecimal amount;
    private TransactionType transactionType;
    private Order order;
}
