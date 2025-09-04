package com.winworld.coursestools.controller;

import com.winworld.coursestools.config.security.UserPrincipal;
import com.winworld.coursestools.dto.transaction.TransactionReadDto;
import com.winworld.coursestools.dto.transaction.TransactionWithdrawDto;
import com.winworld.coursestools.facade.TransactionFacade;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {
    private final TransactionFacade transactionFacade;

    @PostMapping("/withdraw")
    public TransactionReadDto withdraw(
            @AuthenticationPrincipal UserPrincipal principal, @RequestBody @Valid TransactionWithdrawDto dto
    ) {
        return transactionFacade.withdraw(principal.userId(), dto);
    }
}
