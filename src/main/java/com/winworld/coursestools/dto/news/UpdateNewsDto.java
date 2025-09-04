package com.winworld.coursestools.dto.news;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import static com.winworld.coursestools.constants.ValidationMessages.NOT_BLANK_MESSAGE;
import static com.winworld.coursestools.constants.ValidationMessages.NOT_NULL_MESSAGE;

@Data
public class UpdateNewsDto {
    @NotNull(message = NOT_NULL_MESSAGE)
    @NotBlank(message = NOT_BLANK_MESSAGE)
    private String title;
    @NotNull(message = NOT_NULL_MESSAGE)
    @NotBlank(message = NOT_BLANK_MESSAGE)
    private String content;
}
