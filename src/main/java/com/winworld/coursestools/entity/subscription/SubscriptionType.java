package com.winworld.coursestools.entity.subscription;

import com.winworld.coursestools.entity.base.BaseEntity;
import com.winworld.coursestools.enums.SubscriptionName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

import static jakarta.persistence.FetchType.LAZY;

@Entity
@Table(name = "subscription_types")
@Getter
@Setter
public class SubscriptionType extends BaseEntity {
    public static final String NAME = "name";

    @Column(name = "name", length = 32, nullable = false, unique = true)
    @Enumerated(EnumType.STRING)
    private SubscriptionName name;

    @Column(name = "display_name", length = 32, nullable = false)
    private String displayName;

    @OneToMany(mappedBy = "subscriptionType", fetch = LAZY)
    private List<SubscriptionPlan> plans;
}
