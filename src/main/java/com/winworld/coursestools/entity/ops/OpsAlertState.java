package com.winworld.coursestools.entity.ops;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Open/resolved state of one health check, so alerts fire on change instead of on every run. */
@Entity
@Table(name = "ops_alert_state")
@Getter
@Setter
@NoArgsConstructor
public class OpsAlertState {
    @Id
    @Column(name = "check_key", length = 200)
    private String checkKey;

    @Column(name = "open", nullable = false)
    private Boolean open;

    @Column(name = "opened_at")
    private LocalDateTime openedAt;

    @Column(name = "last_notified_at")
    private LocalDateTime lastNotifiedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
