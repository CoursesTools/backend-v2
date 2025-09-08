package com.winworld.coursestools.dto.admin;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

import static com.winworld.coursestools.constants.ValidationMessages.NOT_NULL_MESSAGE;

@Data
public class ChangeUserAccessDto {
    @NotNull(message = NOT_NULL_MESSAGE)
    private Boolean isTrial;
    @NotNull(message = NOT_NULL_MESSAGE)
    private String tradingViewName;
    @NotNull(message = NOT_NULL_MESSAGE)
    private LocalDate expiredAt;
}
