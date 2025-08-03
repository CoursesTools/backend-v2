package com.winworld.coursestools.service.user;

import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.entity.user.UserFinance;
import com.winworld.coursestools.exception.exceptions.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserFinanceServiceTest {

    @Mock
    private UserDataService userDataService;

    @InjectMocks
    private UserFinanceService userFinanceService;

    private User testUser;
    private UserFinance userFinance;
    private final int userId = 1;

    @BeforeEach
    void setUp() {
        // Подготовка тестовых данных
        testUser = new User();
        testUser.setId(userId);

        userFinance = new UserFinance();
        userFinance.setUserId(userId);
        userFinance.setUser(testUser);
        userFinance.setBalance(new BigDecimal("100.00"));

        testUser.setFinance(userFinance);
    }

    @Test
    void decreaseBalance_SufficientFunds_BalanceDecreased() {
        // Arrange
        BigDecimal decreaseAmount = new BigDecimal("50.00");
        BigDecimal expectedBalance = new BigDecimal("50.00");
        when(userDataService.getUserById(userId)).thenReturn(testUser);

        // Act
        userFinanceService.decreaseBalance(userId, decreaseAmount);

        // Assert
        assertEquals(0, expectedBalance.compareTo(userFinance.getBalance()),
                "Balance should be decreased by the specified amount");
        verify(userDataService).getUserById(userId);
    }

    @Test
    void decreaseBalance_ExactFunds_BalanceZero() {
        // Arrange
        BigDecimal decreaseAmount = new BigDecimal("100.00");
        BigDecimal expectedBalance = BigDecimal.ZERO;
        when(userDataService.getUserById(userId)).thenReturn(testUser);

        // Act
        userFinanceService.decreaseBalance(userId, decreaseAmount);

        // Assert
        assertEquals(0, expectedBalance.compareTo(userFinance.getBalance()),
                "Balance should be zero when exact amount is withdrawn");
        verify(userDataService).getUserById(userId);
    }

    @Test
    void decreaseBalance_InsufficientFunds_ThrowsConflictException() {
        // Arrange
        BigDecimal decreaseAmount = new BigDecimal("150.00");
        when(userDataService.getUserById(userId)).thenReturn(testUser);

        // Act & Assert
        ConflictException exception = assertThrows(ConflictException.class,
                () -> userFinanceService.decreaseBalance(userId, decreaseAmount),
                "Should throw ConflictException when funds are insufficient");

        assertEquals("User " + userId + " balance is lower than current balance", exception.getMessage());
        assertEquals(new BigDecimal("100.00"), userFinance.getBalance(),
                "Balance should not be changed when exception is thrown");
        verify(userDataService).getUserById(userId);
    }

    @Test
    void decreaseBalance_ZeroAmount_BalanceUnchanged() {
        // Arrange
        BigDecimal decreaseAmount = BigDecimal.ZERO;
        BigDecimal initialBalance = new BigDecimal("100.00");
        when(userDataService.getUserById(userId)).thenReturn(testUser);

        // Act
        userFinanceService.decreaseBalance(userId, decreaseAmount);

        // Assert
        assertEquals(0, initialBalance.compareTo(userFinance.getBalance()),
                "Balance should remain unchanged when zero amount is withdrawn");
        verify(userDataService).getUserById(userId);
    }
}
