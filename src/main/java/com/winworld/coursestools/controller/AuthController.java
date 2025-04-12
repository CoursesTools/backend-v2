package com.winworld.coursestools.controller;

import com.winworld.coursestools.dto.auth.AuthSignInDto;
import com.winworld.coursestools.dto.auth.AuthSignUpDto;
import com.winworld.coursestools.dto.auth.AuthTokensDto;
import com.winworld.coursestools.dto.recovery.RecoveryDto;
import com.winworld.coursestools.dto.recovery.RecoveryRequestDto;
import com.winworld.coursestools.facade.AuthFacade;
import com.winworld.coursestools.util.EnvironmentUtil;
import com.winworld.coursestools.validation.groups.AuthValidationGroup;
import com.winworld.coursestools.validation.groups.OAuthValidationGroup;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/v1/authorization")
@RequiredArgsConstructor
public class AuthController {
    private static final String REFRESH_TOKEN_COOKIE_NAME = "refresh_token";

    private final AuthFacade authFacade;
    private final EnvironmentUtil environmentUtil;

    @Value("${jwt.refreshLifeTime}")
    private Duration refreshLifeTime;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<AuthTokensDto> signup(
            @RequestBody @Validated(AuthValidationGroup.class)
            AuthSignUpDto dto,
            @RequestHeader(value = "x-forwarded-for", required = false)
            String forwardedFor
    ) {
        return createAuthResponse(authFacade.signup(dto, forwardedFor));
    }

    @PostMapping("/signup/google")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<AuthTokensDto> googleSignUp(
            @RequestBody @Validated(OAuthValidationGroup.class)
            AuthSignUpDto dto,
            @RequestHeader(value = "x-forwarded-for", required = false)
            String forwardedFor
    ) {
        return createAuthResponse(authFacade.googleSignUp(dto, forwardedFor));
    }

    @PostMapping("/signin")
    public ResponseEntity<AuthTokensDto> signin(
            @RequestBody @Validated(AuthValidationGroup.class)
            AuthSignInDto dto
    ) {
        return createAuthResponse(authFacade.signIn(dto));
    }

    @PostMapping("/signin/google")
    public ResponseEntity<AuthTokensDto> googleSignin(
            @RequestBody @Validated(OAuthValidationGroup.class)
            AuthSignInDto dto
    ) {
        return createAuthResponse(authFacade.googleSignIn(dto));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthTokensDto> refresh(
            @CookieValue(name = REFRESH_TOKEN_COOKIE_NAME) String refreshToken
    ) {
        return createAuthResponse(authFacade.refresh(refreshToken));
    }

    @PostMapping("/recovery/request")
    @ResponseStatus(HttpStatus.CREATED)
    public void createRecoveryRequest(@RequestBody @Valid RecoveryRequestDto dto) {
        authFacade.recoveryRequest(dto.getEmail());
    }

    @PostMapping("recovery")
    public void recovery(@RequestBody @Valid RecoveryDto dto) {
        authFacade.recovery(dto);
    }

    private ResponseEntity<AuthTokensDto> createAuthResponse(AuthTokensDto tokens) {
        ResponseCookie refreshTokenCookie = ResponseCookie
                .from(REFRESH_TOKEN_COOKIE_NAME)
                .httpOnly(true)
                .value(tokens.getRefreshToken())
                .maxAge(refreshLifeTime)
                .path("/api")
                .sameSite("Strict")
                .secure(environmentUtil.isProd())
                .build();

        AuthTokensDto responseTokens = new AuthTokensDto(
                null, tokens.getAccessToken()
        );

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString())
                .body(responseTokens);
    }
}
