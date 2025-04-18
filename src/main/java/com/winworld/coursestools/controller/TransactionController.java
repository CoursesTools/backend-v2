package com.winworld.coursestools.controller;

import com.winworld.coursestools.dto.TransactionsAmountDto;
import com.winworld.coursestools.facade.TransactionFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {
    private final TransactionFacade transactionFacade;

    @GetMapping("/sum/{transactionType}")
    @PreAuthorize("hasRole('ADMIN')")
    public TransactionsAmountDto getTransactionsAmount(@PathVariable String transactionType) {
        return transactionFacade.getTransactionsAmount(transactionType);
    }
}
