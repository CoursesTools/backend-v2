package com.winworld.coursestools.service.user;

import com.winworld.coursestools.dto.TransactionsAmountDto;
import com.winworld.coursestools.entity.user.UserTransaction;
import com.winworld.coursestools.enums.Currency;
import com.winworld.coursestools.enums.TransactionType;
import com.winworld.coursestools.repository.UserTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class UserTransactionService {

    private final UserTransactionRepository userTransactionRepository;
    private final UserDataService userDataService;

    public TransactionsAmountDto getTransactionSumAmount(TransactionType transactionType) {
        BigDecimal amount = userTransactionRepository.getTransactionSumAmount(transactionType);
        return new TransactionsAmountDto(
                getPriceInUsd(amount),
                Currency.USD
        );
    }

    public void addTransaction(int userId, BigDecimal amount, TransactionType transactionType) {
        var userTransaction = UserTransaction.builder()
                .transactionType(transactionType)
                .user(userDataService.getUserById(userId))
                .amount(amount)
                .build();
        userTransactionRepository.save(userTransaction);
    }

    public void addPurchaseTransaction(int userId, BigDecimal amount) {
        addTransaction(userId, amount, TransactionType.PURCHASE);
    }

    public void addWithdrawalTransaction(int userId, BigDecimal amount) {
        addTransaction(userId, amount, TransactionType.WITHDRAWAL);
    }

    private Float getPriceInUsd(BigDecimal price) {
        return price
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                .floatValue();
    }
}
