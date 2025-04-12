package com.winworld.coursestools.dto.alert;

import lombok.Data;

import java.util.List;

@Data
public class AlertFilterDto {
    private List<String> types;
    private List<String> brokers;
    private List<String> tfs;
    private List<String> events;
    private List<String> assets;
    private List<String> indicators;
    private Boolean multiAlert;
}
