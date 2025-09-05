package com.winworld.coursestools.controller;

import com.winworld.coursestools.dto.auth.AuthTokensDto;
import com.winworld.coursestools.dto.auth.BasicAuthSignInDto;
import com.winworld.coursestools.dto.auth.BasicAuthSignUpDto;
import com.winworld.coursestools.dto.auth.GoogleAuthSignInDto;
import com.winworld.coursestools.dto.auth.GoogleAuthSignUpDto;
import com.winworld.coursestools.dto.recovery.RecoveryDto;
import com.winworld.coursestools.facade.AuthFacade;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
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

    @Value("${jwt.refreshLifeTime}")
    private Duration refreshLifeTime;

    @Value("${cors.domains.web}")
    private String webDomain;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<AuthTokensDto> signup(
            @RequestBody @Valid BasicAuthSignUpDto dto,
            @RequestHeader(value = "X-Forwarded-For", required = false)
            String forwardedFor,
            HttpServletRequest request
    ) {
        return createAuthResponse(authFacade.signup(dto, forwardedFor != null ? forwardedFor : request.getRemoteAddr()));
    }

    @PostMapping("/signup/google")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<AuthTokensDto> googleSignUp(
            @RequestBody @Valid GoogleAuthSignUpDto dto,
            @RequestHeader(value = "x-forwarded-for", required = false)
            String forwardedFor
    ) {
        return createAuthResponse(authFacade.googleSignUp(dto, forwardedFor));
    }

    @PostMapping("/signin")
    public ResponseEntity<AuthTokensDto> signin(
            @RequestBody @Valid BasicAuthSignInDto dto
    ) {
        return createAuthResponse(authFacade.signIn(dto));
    }

    @DeleteMapping("/signout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> logout() {
        ResponseCookie refreshTokenCookie = createRefreshTokenCookie(null, Duration.ZERO);

        return ResponseEntity
                .noContent()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString())
                .build();
    }

    @PostMapping("/signin/google")
    public ResponseEntity<AuthTokensDto> googleSignin(
            @RequestBody @Valid GoogleAuthSignInDto dto
    ) {
        return createAuthResponse(authFacade.googleSignIn(dto));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthTokensDto> refresh(
            @Parameter(hidden = true)
            @CookieValue(name = REFRESH_TOKEN_COOKIE_NAME) String refreshToken
    ) {
        return createAuthResponse(authFacade.refresh(refreshToken));
    }

    @PostMapping("recovery")
    public void recovery(@RequestBody @Valid RecoveryDto dto) {
        authFacade.recovery(dto);
    }

    private ResponseEntity<AuthTokensDto> createAuthResponse(AuthTokensDto tokens) {
        ResponseCookie refreshTokenCookie = createRefreshTokenCookie(tokens.getRefreshToken(), refreshLifeTime);

        AuthTokensDto responseTokens = new AuthTokensDto(
                null, tokens.getAccessToken()
        );

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString())
                .body(responseTokens);
    }

    private ResponseCookie createRefreshTokenCookie(String refreshToken, Duration refreshLifeTime) {
        return ResponseCookie
                .from(REFRESH_TOKEN_COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .maxAge(refreshLifeTime)
                .sameSite("None")
                .domain("." + webDomain)
                .path("/")
                .secure(true)
                .build();
    }
}
