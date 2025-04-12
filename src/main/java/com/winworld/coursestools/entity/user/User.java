package com.winworld.coursestools.entity.user;

import com.winworld.coursestools.entity.Order;
import com.winworld.coursestools.entity.PromoCode;
import com.winworld.coursestools.entity.base.BaseEntity;
import com.winworld.coursestools.enums.UserRole;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

import static com.winworld.coursestools.enums.UserRole.USER;
import static jakarta.persistence.FetchType.LAZY;

@Getter
@Setter
@Builder
@Entity
@Table(name = "users")
@NoArgsConstructor
@AllArgsConstructor
public class User extends BaseEntity {

    @Column(name = "trading_view_name", length = 32, nullable = false, unique = true)
    private String tradingViewName;

    @Column(name = "email", length = 64, nullable = false, unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 16, nullable = false)
    @Builder.Default
    private UserRole role = USER;

    @Column(name = "password", length = 64, nullable = false)
    private String password;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = false;

    @Column(name = "active_updated_at")
    private LocalDateTime activeUpdatedAt;

    @Column(name = "is_trial_used", nullable = false)
    @Builder.Default
    private boolean isTrialUsed = false;

    @Column(name = "referrer_id", updatable = false, insertable = false)
    private Integer referredId;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, optional = false)
    private UserProfile profile;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, optional = false)
    private UserPartnership partnership;

    @OneToOne(mappedBy = "owner", cascade = CascadeType.ALL, optional = false)
    private PromoCode partnerCode;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
    private UserSubscription subscription;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
    private UserFinance finance;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "referrer_id")
    private User referrer;

    @OneToMany(mappedBy = "referrer", fetch = LAZY)
    private List<User> referrals;

    @OneToMany(mappedBy = "user", fetch = LAZY)
    private List<Order> orders;

    @OneToMany(mappedBy = "user", fetch = LAZY)
    private List<UserTransaction> userTransactions;

    public boolean hasReferrer() {
        return referredId != null || referrer != null;
    }
}
