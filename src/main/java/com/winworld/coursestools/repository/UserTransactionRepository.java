package com.winworld.coursestools.repository;

import com.winworld.coursestools.entity.user.UserTransaction;
import com.winworld.coursestools.enums.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;

public interface UserTransactionRepository extends JpaRepository<UserTransaction, Integer> {

    @Query("""
                    SELECT SUM(ut.amount) FROM UserTransaction ut
                    WHERE ut.transactionType = :transactionType
            """)
    BigDecimal getTransactionSumAmount(TransactionType transactionType);
}
