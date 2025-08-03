package com.winworld.coursestools.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

@AllArgsConstructor
@Data
public class RedirectDto {
    @Schema(requiredMode = REQUIRED)
    private String url;
}
