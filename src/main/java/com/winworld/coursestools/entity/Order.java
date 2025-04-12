package com.winworld.coursestools.entity;

import com.winworld.coursestools.entity.base.BaseEntity;
import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.enums.OrderStatus;
import com.winworld.coursestools.enums.PaymentMethod;
import com.winworld.coursestools.enums.Plan;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "orders")
public class Order extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promo_code")
    private PromoCode promoCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 16, nullable = false)
    private PaymentMethod paymentMethod;

    @Column(name = "original_price", nullable = false)
    private BigDecimal originalPrice;

    @Column(name = "total_price", nullable = false)
    private BigDecimal totalPrice;

    @Column(name = "plan", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    private Plan plan;

    @Column(name = "status", length = 16, nullable = false)
    @Enumerated(EnumType.STRING)
    private OrderStatus status;
}
