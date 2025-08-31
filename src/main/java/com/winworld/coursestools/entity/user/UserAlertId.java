package com.winworld.coursestools.entity.user;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;

@Setter
@Getter
@Embeddable
public class UserAlertId implements Serializable {

    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "alert_id")
    private Integer alertId;

    public UserAlertId() {}

    public UserAlertId(Integer userId, Integer alertId) {
        this.userId = userId;
        this.alertId = alertId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserAlertId)) return false;
        UserAlertId that = (UserAlertId) o;
        return Objects.equals(userId, that.userId) &&
                Objects.equals(alertId, that.alertId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, alertId);
    }
}
