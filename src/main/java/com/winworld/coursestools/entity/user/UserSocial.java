package com.winworld.coursestools.entity.user;

import com.winworld.coursestools.entity.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import static jakarta.persistence.FetchType.LAZY;

@Getter
@Setter
@SuperBuilder
@Entity
@Table(name = "user_socials")
@NoArgsConstructor
@AllArgsConstructor
public class UserSocial extends BaseEntity {

    @Column(name = "trading_view_name", length = 32, nullable = false, unique = true)
    private String tradingViewName;

    @Column(name = "telegram_id", unique = true)
    private String telegramId;

    @Column(name = "discord_id", length = 32, unique = true)
    private String discordId;

    @OneToOne(fetch = LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;
}
