package com.winworld.coursestools.entity;

import com.winworld.coursestools.entity.base.BaseEntity;
import com.winworld.coursestools.entity.user.UserAlert;
import com.winworld.coursestools.entity.user.UserTransaction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

import static jakarta.persistence.FetchType.LAZY;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "alerts")
public class Alert extends BaseEntity {
    public static String TYPE = "type";
    public static String TF = "tf";
    public static String BROKER = "broker";
    public static String EVENT = "event";
    public static String ASSET = "asset";
    public static String INDICATOR = "indicator";
    public static String MULTI_ALERT = "multiAlert";

    @Column(length = 32, nullable = false)
    private String type;
    @Column(length = 32, nullable = false)
    private String broker;
    @Column(length = 16, nullable = false)
    private String tf;
    @Column(length = 32, nullable = false)
    private String event;
    @Column(length = 32, nullable = false)
    private String asset;
    @Column(length = 16, nullable = false)
    private String indicator;
    @Column(nullable = false)
    private Boolean multiAlert;

    @OneToMany(mappedBy = "alert", fetch = LAZY)
    private List<UserAlert> userAlerts;
}
