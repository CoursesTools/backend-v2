package com.winworld.coursestools.entity.user;

import com.winworld.coursestools.entity.Code;
import com.winworld.coursestools.entity.Order;
import com.winworld.coursestools.entity.Referral;
import com.winworld.coursestools.entity.base.BaseEntity;
import com.winworld.coursestools.enums.UserRole;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.proxy.HibernateProxy;

import java.util.List;
import java.util.Objects;

import static com.winworld.coursestools.enums.UserRole.USER;
import static jakarta.persistence.FetchType.LAZY;

@Getter
@Setter
@SuperBuilder
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

    @Column(name = "telegram_id", length = 9, unique = true)
    private String telegramId;

    @OneToOne(mappedBy = "user", optional = false, fetch = LAZY, cascade = CascadeType.ALL)
    private UserProfile profile;

    @OneToOne(mappedBy = "user", optional = false, fetch = LAZY, cascade = CascadeType.ALL)
    private UserPartnership partnership;

    @OneToOne(mappedBy = "user", optional = false, fetch = LAZY, cascade = CascadeType.ALL)
    private UserFinance finance;

    @OneToOne(mappedBy = "owner", optional = false, fetch = LAZY, cascade = CascadeType.ALL)
    private Code partnerCode;

    @OneToOne(mappedBy = "referred", fetch = LAZY)
    private Referral referred;

    @OneToMany(mappedBy = "user", fetch = LAZY)
    private List<UserSubscription> subscriptions;

    @OneToMany(mappedBy = "user", fetch = LAZY)
    private List<Order> orders;

    @OneToMany(mappedBy = "referrer", fetch = LAZY)
    private List<Referral> referrals;

    @OneToMany(mappedBy = "user", fetch = LAZY)
    private List<UserTransaction> userTransactions;

    @OneToMany(mappedBy = "user", fetch = LAZY)
    private List<UserAlert> userAlerts;

    public User(int id) {
        super(id);
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy proxy ? proxy.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy proxy ? proxy.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        User user = (User) o;
        return getId() != null && Objects.equals(getId(), user.getId());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy proxy ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }

    public boolean hasReferrer() {
        return this.getReferred() != null;
    }

    public void addSubscription(UserSubscription userSubscription) {
        if (userSubscription != null) {
            userSubscription.setUser(this);
            this.subscriptions.add(userSubscription);
        }
    }
}
