package com.winworld.coursestools.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

@Data
public class StatisticsAggregation {
    @Schema(requiredMode = REQUIRED)
    private Integer start;
    @Schema(requiredMode = REQUIRED)
    private Integer end;
    @Schema(requiredMode = REQUIRED)
    private Integer delta;
    @Schema(requiredMode = REQUIRED)
    private Float deltaPercent;

    public StatisticsAggregation(Integer start, Integer end) {
        this.start = start;
        this.end = end;
        this.delta = end - start;
        this.deltaPercent = start != 0 ? (float) delta / start * 100 : 100f;
    }
}
