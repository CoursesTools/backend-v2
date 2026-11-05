package com.winworld.coursestools.repository.ops;

import com.winworld.coursestools.entity.ops.OpsTelegramRecipient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OpsTelegramRecipientRepository extends JpaRepository<OpsTelegramRecipient, Integer> {
    List<OpsTelegramRecipient> findAllByOrderByIdAsc();

    boolean existsByTelegramId(Long telegramId);
}
