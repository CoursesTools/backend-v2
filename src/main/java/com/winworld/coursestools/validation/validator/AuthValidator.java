package com.winworld.coursestools.validation.validator;

import com.winworld.coursestools.dto.auth.BasicAuthSignInDto;
import com.winworld.coursestools.dto.auth.BasicAuthSignUpDto;
import com.winworld.coursestools.dto.auth.GoogleAuthSignUpDto;
import com.winworld.coursestools.exception.exceptions.BusinessException;
import com.winworld.coursestools.exception.exceptions.EntityAlreadyExistException;
import com.winworld.coursestools.service.user.UserDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthValidator {
    private final UserDataService userDataService;
    private final PasswordEncoder passwordEncoder;

    public void validateSignUp(BasicAuthSignUpDto dto) {
        validateReferrerCode(dto.getReferrerCode());
        validateEmailNotExists(dto.getEmail());
        validateTradingViewNameNotExists(dto.getTradingViewName());
    }

    public void validateGoogleSignUp(GoogleAuthSignUpDto dto) {
        validateReferrerCode(dto.getReferrerCode());
        validateTradingViewNameNotExists(dto.getTradingViewName());
    }

    public void validateSignIn(BasicAuthSignInDto dto, String userPassword) {
        if (!passwordEncoder.matches(dto.getPassword(), userPassword)) {
            throw new BusinessException("Invalid password");
        }
    }

    public boolean existsByEmail(String email) {
        return userDataService.existsByEmail(email);
    }

    public boolean existsByTradingViewName(String tradingViewName) {
        return userDataService.existsByTradingViewName(tradingViewName);
    }

    private void validateReferrerCode(String referrerCode) {
        if (referrerCode != null && referrerCode.isBlank()) {
            throw new BusinessException("Referrer code cannot be empty");
        }
    }

    private void validateEmailNotExists(String email) {
        if (existsByEmail(email)) {
            throw new EntityAlreadyExistException("Email already exists");
        }
    }

    private void validateTradingViewNameNotExists(String tradingViewName) {
        if (existsByTradingViewName(tradingViewName)) {
            throw new EntityAlreadyExistException("TradingView name already exists");
        }
    }
}
