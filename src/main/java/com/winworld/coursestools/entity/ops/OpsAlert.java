package com.winworld.coursestools.entity.ops;

import com.winworld.coursestools.enums.OpsAlertCategory;
import com.winworld.coursestools.enums.OpsAlertSeverity;
import com.winworld.coursestools.enums.OpsTelegramStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** One system alert. Always stored; {@link #telegramStatus} records what Telegram delivery did. */
@Entity
@Table(name = "ops_alerts")
@Getter
@Setter
@NoArgsConstructor
public class OpsAlert {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 32)
    private OpsAlertCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 16)
    private OpsAlertSeverity severity;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "dedup_key", length = 200)
    private String dedupKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "telegram_status", nullable = false, length = 32)
    private OpsTelegramStatus telegramStatus;

    @Column(name = "telegram_detail", length = 500)
    private String telegramDetail;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
