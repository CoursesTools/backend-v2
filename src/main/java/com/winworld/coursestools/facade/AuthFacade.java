package com.winworld.coursestools.facade;

import com.winworld.coursestools.dto.auth.AuthTokensDto;
import com.winworld.coursestools.dto.auth.BasicAuthSignInDto;
import com.winworld.coursestools.dto.auth.BasicAuthSignUpDto;
import com.winworld.coursestools.dto.auth.GoogleAuthSignInDto;
import com.winworld.coursestools.dto.auth.GoogleAuthSignUpDto;
import com.winworld.coursestools.dto.recovery.RecoveryDto;
import com.winworld.coursestools.exception.exceptions.DataValidationException;
import com.winworld.coursestools.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthFacade {
    private final AuthService authService;

    public AuthTokensDto signup(BasicAuthSignUpDto dto, String forwardedFor) {
        validatePasswords(dto.getPassword(), dto.getConfirmPassword());
        return authService.signup(dto, forwardedFor);
    }

    public AuthTokensDto googleSignUp(GoogleAuthSignUpDto dto, String forwardedFor) {
        return authService.googleSignup(dto, forwardedFor);
    }

    public AuthTokensDto signIn(BasicAuthSignInDto dto) {
        return authService.signIn(dto);
    }

    public AuthTokensDto googleSignIn(GoogleAuthSignInDto dto) {
        return authService.googleSignIn(dto);
    }

    public AuthTokensDto refresh(String refreshToken) {
        return authService.refresh(refreshToken);
    }

    public void recovery(RecoveryDto dto) {
        authService.recovery(dto);
    }

    private void validatePasswords(String password, String confirmPassword) {
        if (!password.equals(confirmPassword)) {
            throw new DataValidationException("Password and confirm password do not match");
        }
    }
}