package com.winworld.coursestools.dto.alert;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class AlertSubscribeDto {
    private List<Integer> alertsIds;
    private Map<String, Object> properties;
}
