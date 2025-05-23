package com.winworld.coursestools.entity.user;

import com.winworld.coursestools.entity.base.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import static jakarta.persistence.FetchType.LAZY;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "user_partnership")
public class UserPartnership extends Auditable {
    @Id
    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "terms_accepted", nullable = false)
    @Builder.Default
    private Boolean termsAccepted = false;

    @Column(name = "level_rank", nullable = false)
    private Integer level;

    @OneToOne(fetch = LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    public void levelUp() {
        this.level++;
    }
}
