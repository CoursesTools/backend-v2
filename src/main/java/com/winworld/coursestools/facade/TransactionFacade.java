package com.winworld.coursestools.facade;

import com.winworld.coursestools.dto.transaction.TransactionReadDto;
import com.winworld.coursestools.dto.transaction.TransactionWithdrawDto;
import com.winworld.coursestools.dto.transaction.TransactionsAmountDto;
import com.winworld.coursestools.enums.TransactionType;
import com.winworld.coursestools.service.user.UserTransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TransactionFacade {
    private final UserTransactionService userTransactionService;

    public TransactionsAmountDto getTransactionsAmount(String transactionTypeString) {
        TransactionType transactionType = TransactionType.fromString(
                transactionTypeString
        );
        return userTransactionService.getTransactionSumAmount(transactionType);
    }

    public TransactionReadDto withdraw(int userId, TransactionWithdrawDto dto) {
        return userTransactionService.withdraw(userId, dto);
    }
}
