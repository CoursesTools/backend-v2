package com.winworld.coursestools.dto.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthTokensDto {
    @Schema(hidden = true)
    private String refreshToken;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String accessToken;
}
