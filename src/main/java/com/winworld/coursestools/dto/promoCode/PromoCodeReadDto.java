package com.winworld.coursestools.dto.promoCode;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@AllArgsConstructor
public class PromoCodeReadDto {
    private int id;
    private String code;
    private int monthDiscount;
    private int yearDiscount;
    private int lifetimeDiscount;
    @JsonProperty("owner_id")
    private int ownerId;
}
