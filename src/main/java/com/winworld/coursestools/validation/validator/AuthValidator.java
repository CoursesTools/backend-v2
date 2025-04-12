package com.winworld.coursestools.validation.validator;

import com.winworld.coursestools.dto.auth.AuthSignInDto;
import com.winworld.coursestools.dto.auth.AuthSignUpDto;
import com.winworld.coursestools.exception.BusinessException;
import com.winworld.coursestools.exception.EntityAlreadyExistException;
import com.winworld.coursestools.service.user.UserDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthValidator {
    private final UserDataService userDataService;
    private final PasswordEncoder passwordEncoder;

    public void validateSignUp(AuthSignUpDto dto) {
        if (dto.getReferrerCode() != null && dto.getReferrerCode().isBlank()) {
            throw new BusinessException("Referrer code cannot be empty");
        }
        if (existsByEmail(dto.getEmail())) {
            throw new EntityAlreadyExistException("Email already exists");
        }
        if (existsByTradingViewName(dto.getTradingViewName())) {
            throw new EntityAlreadyExistException("TradingView name already exists");
        }

    }

    public void validateSignIn(AuthSignInDto dto, String userPassword) {
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
}
