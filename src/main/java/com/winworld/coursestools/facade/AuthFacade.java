package com.winworld.coursestools.facade;

import com.winworld.coursestools.dto.auth.AuthSignInDto;
import com.winworld.coursestools.dto.auth.AuthSignUpDto;
import com.winworld.coursestools.dto.auth.AuthTokensDto;
import com.winworld.coursestools.dto.recovery.RecoveryDto;
import com.winworld.coursestools.exception.DataValidationException;
import com.winworld.coursestools.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthFacade {
    private final AuthService authService;

    public AuthTokensDto signup(AuthSignUpDto dto, String forwardedFor) {
        validatePasswords(dto.getPassword(), dto.getConfirmPassword());
        return authService.signup(dto, forwardedFor);
    }

    public AuthTokensDto googleSignUp(AuthSignUpDto dto, String forwardedFor) {
        return authService.googleSignup(dto, forwardedFor);
    }

    public AuthTokensDto signIn(AuthSignInDto dto) {
        return authService.signIn(dto);
    }

    public AuthTokensDto googleSignIn(AuthSignInDto dto) {
        return authService.googleSignIn(dto);
    }

    public AuthTokensDto refresh(String refreshToken) {
        return authService.refresh(refreshToken);
    }

    public void recoveryRequest(String email) {
        authService.recoveryRequest(email);
    }

    public void recovery(RecoveryDto dto) {
        validatePasswords(dto.getPassword(), dto.getConfirmPassword());
        authService.recovery(dto);
    }

    private void validatePasswords(String password, String confirmPassword) {
        if (!password.equals(confirmPassword)) {
            throw new DataValidationException("Password and confirm password do not match");
        }
    }
}