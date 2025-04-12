package com.winworld.coursestools.exception;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

@Data
@AllArgsConstructor
public class ErrorDto {
    @Schema(requiredMode = REQUIRED)
    private String field;
    @Schema(requiredMode = REQUIRED)
    private String code;
    @Schema(requiredMode = REQUIRED)
    private String message;
    @Schema(requiredMode = REQUIRED)
    private Object rejectedValue;
}
