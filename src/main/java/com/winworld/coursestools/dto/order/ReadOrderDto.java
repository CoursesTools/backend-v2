package com.winworld.coursestools.dto.order;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.winworld.coursestools.enums.PaymentMethod;
import com.winworld.coursestools.enums.Plan;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReadOrderDto {
    private int id;
    private int userId;
    @JsonProperty("payment_method")
    private PaymentMethod paymentMethod;
    @JsonProperty("payment_link")
    private String paymentLink;
    private Plan plan;
    @JsonProperty("total_price")
    private Integer totalPrice;
    @JsonProperty("original_price")
    private Integer originalPrice;
    @JsonProperty("created_at")
    private LocalDateTime createdAt;
}
