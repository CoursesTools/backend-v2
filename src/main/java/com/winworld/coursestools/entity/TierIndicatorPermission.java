package com.winworld.coursestools.entity;

import com.winworld.coursestools.entity.base.BaseEntity;
import com.winworld.coursestools.entity.subscription.SubscriptionType;
import com.winworld.coursestools.enums.SubscriptionTier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import static jakarta.persistence.FetchType.LAZY;

@Entity
@Table(name = "tier_indicator_permissions")
@Getter
@Setter
@NoArgsConstructor
public class TierIndicatorPermission extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "tier", length = 32, nullable = false)
    private SubscriptionTier tier;

    @Column(name = "indicator", length = 32, nullable = false)
    private String indicator;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "subscription_type_id", nullable = false)
    private SubscriptionType subscriptionType;
}
