package com.winworld.coursestools.dto.news;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

@Data
public class NewsReadDto {
    @Schema(requiredMode = REQUIRED)
    private int id;
    @Schema(requiredMode = REQUIRED)
    private String title;
    @Schema(requiredMode = REQUIRED)
    private String content;
    @Schema(requiredMode = REQUIRED)
    private LocalDateTime createdAt;
}
