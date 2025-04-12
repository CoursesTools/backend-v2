package com.winworld.coursestools.dto.alert;

import lombok.Data;

import java.util.Map;

@Data
public class AlertSubscribeDto extends AlertFilterDto {
    private Map<String, Object> properties;
}
