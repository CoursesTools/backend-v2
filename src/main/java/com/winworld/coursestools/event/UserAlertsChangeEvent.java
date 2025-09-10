package com.winworld.coursestools.event;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserAlertsChangeEvent {
    private String telegramId;
}
