package com.winworld.coursestools.dto.payment;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import static com.winworld.coursestools.constants.ValidationMessages.NOT_NULL_MESSAGE;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BalanceRetrieveDto {
    @NotNull(message = NOT_NULL_MESSAGE)
    private Integer orderId;
    private Integer userId;
}
