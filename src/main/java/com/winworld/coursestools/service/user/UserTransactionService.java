package com.winworld.coursestools.service.user;

import com.winworld.coursestools.dto.transaction.TransactionReadDto;
import com.winworld.coursestools.dto.transaction.TransactionWithdrawDto;
import com.winworld.coursestools.dto.transaction.TransactionsAmountDto;
import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.entity.user.UserTransaction;
import com.winworld.coursestools.enums.Currency;
import com.winworld.coursestools.enums.TransactionType;
import com.winworld.coursestools.exception.exceptions.ConflictException;
import com.winworld.coursestools.mapper.TransactionMapper;
import com.winworld.coursestools.repository.user.UserTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class UserTransactionService {

    private final UserTransactionRepository userTransactionRepository;
    private final UserDataService userDataService;
    private final TransactionMapper transactionMapper;

    public TransactionsAmountDto getTransactionSumAmount(TransactionType transactionType) {
        BigDecimal amount = userTransactionRepository.getTransactionSumAmount(transactionType);
        return new TransactionsAmountDto(
                getPriceInUsd(amount),
                Currency.USD
        );
    }

    public UserTransaction addTransaction(int userId, BigDecimal amount, TransactionType transactionType) {
        var userTransaction = UserTransaction.builder()
                .transactionType(transactionType)
                .user(userDataService.getUserById(userId))
                .amount(amount)
                .build();
        return userTransactionRepository.save(userTransaction);
    }

    public UserTransaction addPurchaseTransaction(int userId, BigDecimal amount) {
        return addTransaction(userId, amount, TransactionType.PURCHASE);
    }

    public void addWithdrawalTransaction(int userId, BigDecimal amount) {
        addTransaction(userId, amount, TransactionType.WITHDRAWAL);
    }

    private Float getPriceInUsd(BigDecimal price) {
        return price
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                .floatValue();
    }

    @Transactional
    public TransactionReadDto withdraw(int userId, TransactionWithdrawDto dto) {
        User user = userDataService.getUserById(userId);
        if (user.getFinance().getBalance().compareTo(dto.getAmount()) < 0) {
            throw new ConflictException("Insufficient balance for withdrawal");
        }
        user.getFinance().addBalance(dto.getAmount().negate());
        var transaction = addTransaction(userId, dto.getAmount(), TransactionType.WITHDRAWAL);
        //TODO залогировать и отправить запрос
        return transactionMapper.toDto(transaction);
    }
}
