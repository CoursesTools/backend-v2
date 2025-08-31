package com.winworld.coursestools.entity.user;

import com.winworld.coursestools.entity.Alert;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "users_alerts")
public class UserAlert {
    public static final String ALERT = "alert";
    public static final String USER = "user";

    @EmbeddedId
    private UserAlertId id;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @MapsId("alertId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alert_id")
    private Alert alert;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> properties;
}
