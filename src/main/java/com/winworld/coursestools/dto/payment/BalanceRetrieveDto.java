package com.winworld.coursestools.dto.payment;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BalanceRetrieveDto {
    private static final String NOT_NULL_MESSAGE = "Field must not be null";

    @NotNull(message = NOT_NULL_MESSAGE)
    private Integer orderId;
    private Integer userId;
}
