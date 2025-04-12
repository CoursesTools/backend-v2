package com.winworld.coursestools.entity.subscription;

import com.winworld.coursestools.entity.base.BaseEntity;
import com.winworld.coursestools.entity.user.UserSubscription;
import com.winworld.coursestools.enums.Plan;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.proxy.HibernateProxy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import static jakarta.persistence.FetchType.LAZY;

@Entity
@Table(name = "subscription_plans")
@Getter
@Setter
public class SubscriptionPlan extends BaseEntity {
    public static final String SUBSCRIPTION_TYPE = "subscriptionType";

    @Column(name = "name",length = 32, nullable = false)
    @Enumerated(EnumType.STRING)
    private Plan name;

    @Column(name = "display_name", length = 32, nullable = false)
    private String displayName;

    @Column(name = "duration_days", nullable = false)
    private Integer durationDays;

    @Column(name = "price", nullable = false)
    private BigDecimal price;

    @Column(name = "discount_multiplier", nullable = false)
    private BigDecimal discountMultiplier;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "subscription_type_id")
    private SubscriptionType subscriptionType;

    @OneToMany(mappedBy = "plan", fetch = LAZY)
    private List<UserSubscription> subscriptions;

    @OneToMany(mappedBy = "plan", fetch = LAZY)
    private List<UserSubscription> orders;

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy proxy ? proxy.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy proxy ? proxy.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        SubscriptionPlan plan = (SubscriptionPlan) o;
        return getId() != null && Objects.equals(getId(), plan.getId());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy proxy ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
