package com.winworld.coursestools.dto.alert;

import lombok.Data;

import java.util.List;

@Data
public class AlertSubscriptionCategoriesDto {
    private List<Type> types;
    private List<String> events;
    private List<String> timeFrames;

    public record Type(String type, List<String> assets, List<String> brokers) {}
}
