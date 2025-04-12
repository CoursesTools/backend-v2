package com.winworld.coursestools.exception;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class ErrorResponse {
    @Schema(requiredMode = REQUIRED)
    private int status;
    @Schema(requiredMode = REQUIRED)
    private String error;
    @Schema(requiredMode = REQUIRED)
    private String message;
    @Schema(requiredMode = REQUIRED)
    private LocalDateTime timestamp = LocalDateTime.now();
    @Schema(requiredMode = NOT_REQUIRED)
    private List<ErrorDto> errors;

    public ErrorResponse(HttpStatus status, String error, String message) {
        this.status = status.value();
        this.error = error;
        this.message = message;
    }

    public ErrorResponse(HttpStatus status, String error, String message, List<ErrorDto> errors) {
        this(status, error, message);
        this.errors = errors;
    }
}
