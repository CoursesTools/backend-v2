package com.winworld.coursestools.dto.admin;

import com.winworld.coursestools.enums.TradingViewRetryJobStatus;
import com.winworld.coursestools.enums.TradingViewRetryJobType;
import lombok.Data;

@Data
public class TradingViewRetryJobFilterDto {
    private Integer userId;
    private TradingViewRetryJobStatus status;
    private TradingViewRetryJobType type;
}
