package com.winworld.coursestools.entity.ops;

import com.winworld.coursestools.enums.OpsAlertCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "ops_alert_categories")
@Getter
@Setter
@NoArgsConstructor
public class OpsAlertCategorySetting {
    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 32)
    private OpsAlertCategory category;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private Integer updatedBy;
}
