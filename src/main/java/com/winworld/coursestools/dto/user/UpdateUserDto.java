package com.winworld.coursestools.dto.user;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateUserDto {
    private static final String DISCORD_ID_MESSAGE = "Discord ID must be between 17 and 32 characters long";
    private static final String PARTNER_CODE_MESSAGE = "Partner code must be between 3 and 32 characters long";

    @Size(min = 3, max = 32, message = PARTNER_CODE_MESSAGE)
    private String partnerCode;
    private Boolean termsAccepted;
}
