package com.winworld.coursestools.dto.ops;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class AdminAuditEntryDto {
    @Schema(requiredMode = REQUIRED)
    private Long id;
    @Schema(requiredMode = REQUIRED)
    private String action;
    private String target;
    private Map<String, Object> before;
    private Map<String, Object> after;
    private String actorEmail;
    @Schema(requiredMode = REQUIRED, description = "UTC")
    private LocalDateTime createdAt;
}
