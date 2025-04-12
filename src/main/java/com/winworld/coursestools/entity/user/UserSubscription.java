package com.winworld.coursestools.entity.user;

import com.winworld.coursestools.entity.base.Auditable;
import com.winworld.coursestools.enums.PaymentMethod;
import com.winworld.coursestools.enums.Plan;
import com.winworld.coursestools.enums.SubscriptionStatus;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
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
@Table(name = "user_subscription")
public class UserSubscription extends Auditable {
    @Id
    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "plan", length = 16, nullable = false)
    @Enumerated(EnumType.STRING)
    private Plan plan;

    @Column(name = "subscription_price", nullable = false)
    private BigDecimal subscriptionPrice;

    @Column(name = "payment_method", length = 16)
    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    @Column(name = "status", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    private SubscriptionStatus status;

    @Column(name = "expired_at", nullable = false)
    private LocalDateTime expiredAt;

    @Type(JsonBinaryType.class)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payment_provider_data", columnDefinition = "jsonb")
    private Map<String, Object> paymentProviderData;

    @Column(name = "is_trial", nullable = false)
    @Builder.Default
    private boolean isTrial = false;

    @OneToOne(fetch = LAZY)
    @JoinColumn(name = "user_id")
    @MapsId
    private User user;
}
