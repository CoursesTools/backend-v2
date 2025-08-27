package com.winworld.coursestools.service.user;

import com.winworld.coursestools.dto.transaction.TransactionCreateDto;
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
import java.time.LocalDate;
import java.time.LocalTime;

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

    public TransactionsAmountDto getTransactionSumAmount(TransactionType transactionType, LocalDate startDate, LocalDate endDate) {
        BigDecimal amount;
        var startDateTime = startDate != null ? startDate.atStartOfDay() : null;
        var endDateTime = endDate != null ? endDate.atTime(LocalTime.MAX) : null;

        if (startDateTime != null && endDateTime != null) {
            amount = userTransactionRepository.getTransactionSumAmountBetween(
                    transactionType,
                    startDateTime,
                    endDateTime
            );
        } else if (startDateTime != null) {
            amount = userTransactionRepository.getTransactionSumAmountFrom(transactionType, startDateTime);
        } else if (endDateTime != null) {
            amount = userTransactionRepository.getTransactionSumAmountTo(transactionType, endDateTime);
        } else {
            amount = userTransactionRepository.getTransactionSumAmount(transactionType);
        }
        return new TransactionsAmountDto(
                getPriceInUsd(amount),
                Currency.USD
        );
    }

    public UserTransaction getById(int transactionId) {
        return userTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new EntityNotFoundException("Transaction not found with id: " + transactionId));
    }

    public UserTransaction addTransaction(TransactionCreateDto dto) {
        var userTransaction = transactionMapper.toEntity(dto);
        return userTransactionRepository.save(userTransaction);
    }

    private BigDecimal getPriceInUsd(BigDecimal price) {
        return price == null || price.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : price
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    @Transactional
    public TransactionReadDto withdraw(int userId, TransactionWithdrawDto dto) {
        User user = userDataService.getUserById(userId);
        if (user.getFinance().getBalance().compareTo(dto.getAmount()) < 0) {
            throw new ConflictException("Insufficient balance for withdrawal");
        }
        user.getFinance().addBalance(dto.getAmount().negate());
        var transaction = addTransaction(new TransactionCreateDto(user, dto.getAmount(), TransactionType.WITHDRAWAL, null));

        WithdrawRequestDto withdrawalRequest = transactionMapper.toDto(transaction, dto.getWallet(), withdrawalSecret);
        withdrawalRequest.setAmount(getPriceInUsd(dto.getAmount()));
        requestWithdrawal(withdrawalRequest);

        log.info("Withdrawal request processed for transaction: {}", transaction.getId());
        return transactionMapper.toDto(transaction);
    }

    @Retry(name = "client-error-included", fallbackMethod = "handleFallback")
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
