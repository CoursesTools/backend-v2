package com.winworld.coursestools.service.user;

import com.winworld.coursestools.entity.user.UserFinance;
import com.winworld.coursestools.exception.ConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class UserFinanceService {

    private final UserDataService userDataService;

    @Transactional
    public void decreaseBalance(int userId, BigDecimal amount) {
        UserFinance userFinance = userDataService.getUserById(userId).getFinance();
        if (userFinance.getBalance().compareTo(amount) < 0) {
            throw new ConflictException("User " + userId + " balance is lower than current balance");
        }
        userFinance.decreaseBalance(amount);
    }
}
