package com.winworld.coursestools.dto.admin;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

import static com.winworld.coursestools.constants.ValidationMessages.NOT_NULL_MESSAGE;

@Data
public class DirectAccessRequestDto {
    @NotBlank
    private String tradingViewName;

    @NotNull(message = NOT_NULL_MESSAGE)
    @Future
    private LocalDate expiredAt;
}
