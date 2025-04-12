package com.winworld.coursestools.entity;

import com.winworld.coursestools.entity.base.BaseEntity;
import com.winworld.coursestools.entity.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
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
@Table(name = "promo_codes")
public class PromoCode extends BaseEntity {

    @Column(name = "code", length = 32, unique = true, nullable = false)
    private String code;

    @Column(name = "month_discount", nullable = false)
    private Integer monthDiscount;

    @Column(name = "year_discount", nullable = false)
    private Integer yearDiscount;

    @Column(name = "lifetime_discount", nullable = false)
    private Integer lifetimeDiscount;

    @OneToOne
    @JoinColumn(name = "owner_id", unique = true)
    private User owner;

    @OneToMany(mappedBy = "promoCode", fetch = LAZY)
    private List<Order> orders;

    public boolean isPartnershipCode() {
        return this.owner != null;
    }
}
