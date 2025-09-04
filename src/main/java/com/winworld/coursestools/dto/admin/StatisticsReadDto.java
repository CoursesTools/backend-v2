package com.winworld.coursestools.dto.admin;

import com.winworld.coursestools.dto.transaction.TransactionsAmountDto;
import com.winworld.coursestools.enums.Plan;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

@Data
@Builder
public class StatisticsReadDto {
    @Schema(requiredMode = REQUIRED)
    private StatisticsAggregation activeUsers;
    @Schema(requiredMode = REQUIRED)
    private TransactionsAmountDto revenue;
    @Schema(requiredMode = REQUIRED)
    private TransactionsAmountDto payout;
    @Schema(requiredMode = REQUIRED)
    private Map<Plan, StatisticsAggregation> planDistribution;
}
