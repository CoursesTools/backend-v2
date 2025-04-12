package com.winworld.coursestools.entity.user;

import com.winworld.coursestools.entity.base.BaseEntity;
import com.winworld.coursestools.entity.subscription.SubscriptionPlan;
import com.winworld.coursestools.enums.PaymentMethod;
import com.winworld.coursestools.enums.SubscriptionStatus;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static jakarta.persistence.FetchType.LAZY;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "users_subscriptions")
public class UserSubscription extends BaseEntity {
    public static final String STATUS = "status";
    public static final String IS_TRIAL = "isTrial";
    public static final String USER = "user";
    public static final String PLAN = "plan";

    @Column(name = "price")
    private BigDecimal price;

    @Column(name = "payment_method", length = 16)
    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    @Column(name = "status", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    private SubscriptionStatus status;

    @Column(name = "is_trial", nullable = false)
    private Boolean isTrial;

    @Column(name = "expired_at", nullable = false)
    private LocalDateTime expiredAt;

    @Type(JsonBinaryType.class)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payment_provider_data", columnDefinition = "jsonb")
    private Map<String, Object> paymentProviderData;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private SubscriptionPlan plan;
}
