package com.winworld.coursestools.service.user;

import com.winworld.coursestools.dto.transaction.TransactionReadDto;
import com.winworld.coursestools.dto.transaction.TransactionWithdrawDto;
import com.winworld.coursestools.dto.transaction.TransactionsAmountDto;
import com.winworld.coursestools.dto.transaction.WithdrawRequestDto;
import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.entity.user.UserTransaction;
import com.winworld.coursestools.enums.Currency;
import com.winworld.coursestools.enums.TransactionType;
import com.winworld.coursestools.exception.exceptions.ConflictException;
import com.winworld.coursestools.exception.exceptions.EntityNotFoundException;
import com.winworld.coursestools.exception.exceptions.ExternalServiceException;
import com.winworld.coursestools.mapper.TransactionMapper;
import com.winworld.coursestools.repository.user.UserTransactionRepository;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserTransactionService {
    private final RestTemplate restTemplate;
    private final UserTransactionRepository userTransactionRepository;
    private final UserDataService userDataService;
    private final TransactionMapper transactionMapper;

    @Value("${urls.withdrawal}")
    private String withdrawalUrl;

    @Value("${secrets.withdrawal-secret}")
    private String withdrawalSecret;

    public TransactionsAmountDto getTransactionSumAmount(TransactionType transactionType) {
        BigDecimal amount = userTransactionRepository.getTransactionSumAmount(transactionType);
        return new TransactionsAmountDto(
                getPriceInUsd(amount),
                Currency.USD
        );
    }

    public UserTransaction getById(int transactionId) {
        return userTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new EntityNotFoundException("Transaction not found with id: " + transactionId));
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

    public UserTransaction addWithdrawalTransaction(int userId, BigDecimal amount) {
        return addTransaction(userId, amount, TransactionType.WITHDRAWAL);
    }

    private BigDecimal getPriceInUsd(BigDecimal price) {
        return price
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    @Transactional
    public TransactionReadDto withdraw(int userId, TransactionWithdrawDto dto) {
        User user = userDataService.getUserById(userId);
        if (user.getFinance().getBalance().compareTo(dto.getAmount()) < 0) {
            throw new ConflictException("Insufficient balance for withdrawal");
        }
        user.getFinance().addBalance(dto.getAmount().negate());
        var transaction = addWithdrawalTransaction(userId, dto.getAmount());

        WithdrawRequestDto withdrawalRequest = transactionMapper.toDto(transaction, dto.getWallet(), withdrawalSecret);
        withdrawalRequest.setAmount(getPriceInUsd(dto.getAmount()));
        requestWithdrawal(withdrawalRequest);

        log.info("Withdrawal request processed for transaction: {}", transaction.getId());
        return transactionMapper.toDto(transaction);
    }

    @Retry(name = "default", fallbackMethod = "handleFallback")
    private void requestWithdrawal(WithdrawRequestDto dto) {
        restTemplate.postForObject(
                withdrawalUrl,
                dto,
                Void.class
        );
    }

    private String handleFallback(WithdrawRequestDto dto, Throwable throwable) {
        log.error(
                "Error while get request for withdraw: {}",
                dto.getTransactionId(),
                throwable
        );
        throw new ExternalServiceException("Withdrawal request failed, please try again later");
    }
}
