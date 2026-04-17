package com.winworld.coursestools.dto.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.winworld.coursestools.enums.TradingViewRetryJobStatus;
import com.winworld.coursestools.enums.TradingViewRetryJobType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class TradingViewRetryJobReadDto {
    @Schema(requiredMode = REQUIRED)
    private Integer id;
    @Schema(requiredMode = REQUIRED)
    private Integer userId;
    private String userEmail;
    private String tradingViewName;
    @Schema(requiredMode = REQUIRED)
    private TradingViewRetryJobType type;
    @Schema(requiredMode = REQUIRED)
    private TradingViewRetryJobStatus status;
    @Schema(requiredMode = REQUIRED)
    private Integer attempts;
    @Schema(requiredMode = REQUIRED)
    private LocalDateTime nextAttemptAt;
    @Schema(requiredMode = REQUIRED)
    private LocalDateTime firstEnqueuedAt;
    private String lastError;
    @Schema(requiredMode = REQUIRED, description = "Raw JSON payload that will be POSTed to the TradingView bot on retry")
    private String payload;
}
