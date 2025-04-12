package com.winworld.coursestools.service;

import com.winworld.coursestools.dto.auth.AuthTokensDto;
import com.winworld.coursestools.dto.auth.BasicAuthSignInDto;
import com.winworld.coursestools.dto.auth.BasicAuthSignUpDto;
import com.winworld.coursestools.dto.auth.GoogleAuthSignInDto;
import com.winworld.coursestools.dto.auth.GoogleAuthSignUpDto;
import com.winworld.coursestools.dto.recovery.RecoveryDto;
import com.winworld.coursestools.dto.recovery.RecoveryEmailDto;
import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.entity.user.UserFinance;
import com.winworld.coursestools.entity.user.UserPartnership;
import com.winworld.coursestools.entity.user.UserProfile;
import com.winworld.coursestools.enums.UserRole;
import com.winworld.coursestools.exception.exceptions.SecurityException;
import com.winworld.coursestools.mapper.AuthMapper;
import com.winworld.coursestools.mapper.UserMapper;
import com.winworld.coursestools.messaging.auth.RecoveryMessageBuilder;
import com.winworld.coursestools.service.external.OAuthGoogleService;
import com.winworld.coursestools.service.user.UserDataService;
import com.winworld.coursestools.util.StringGeneratorUtil;
import com.winworld.coursestools.util.jwt.impl.AuthJwtTokenUtil;
import com.winworld.coursestools.validation.validator.AuthValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final AuthJwtTokenUtil jwtTokenUtil;
    private final AuthValidator authValidator;
    private final AuthMapper authMapper;
    private final UserMapper userMapper;
    private final UserDataService userDataService;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final OAuthGoogleService oAuthGoogleService;
    private final RecoveryMessageBuilder recoveryMessageBuilder;
    private final EmailService emailService;
    private final TokenService tokenService;
    private final StringGeneratorUtil stringGeneratorUtil;
    private final CodeService codeService;
    private final ReferralService referralService;

    @Value("${jwt.refreshLifeTime}")
    private Duration refreshLifeTime;

    @Value("${jwt.accessLifeTime}")
    private Duration accessLifetime;

    @Value("${urls.web-recovery}")
    private String webRecoveryUrl;

    @Transactional
    public AuthTokensDto signup(BasicAuthSignUpDto dto, String forwardedFor) {
        authValidator.validateSignUp(dto);
        var user = authMapper.toEntity(dto);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));

        User savedUser = setupAndSaveUser(user, dto.getReferrerCode());

        eventPublisher.publishEvent(
                userMapper.toEvent(savedUser, forwardedFor)
        );
        return generateAuthTokens(savedUser.getId(), savedUser.getRole());
    }

    @Transactional
    public AuthTokensDto googleSignup(GoogleAuthSignUpDto dto, String forwardedFor) {
        var googleUserInfo = oAuthGoogleService.getUserInfo(dto.getAuthorizationCode());
        if (!googleUserInfo.isEmailVerified()) {
            throw new SecurityException("Email not verified by Google");
        }

        authValidator.validateGoogleSignUp(dto);

        var user = new User();
        user.setEmail(googleUserInfo.getEmail());
        user.setTradingViewName(dto.getTradingViewName());
        var password = stringGeneratorUtil.generatePassword();
        user.setPassword(passwordEncoder.encode(password));

        User savedUser = setupAndSaveUser(user, dto.getReferrerCode());

        eventPublisher.publishEvent(
                userMapper.toEvent(savedUser, forwardedFor, password)
        );
        return generateAuthTokens(savedUser.getId(), savedUser.getRole());
    }

    private User setupAndSaveUser(User user, String referrerCode) {
        var userProfile = new UserProfile();
        var userPartnership = new UserPartnership();
        var userFinance = new UserFinance();

        user.setRole(UserRole.USER);
        userPartnership.setLevel(0);

        userPartnership.setUser(user);
        userProfile.setUser(user);
        userFinance.setUser(user);
        user.setProfile(userProfile);
        user.setPartnership(userPartnership);
        user.setFinance(userFinance);

        User savedUser = userDataService.save(user);
        if (referrerCode != null) {
            referralService.registerReferral(referrerCode, savedUser, false);
        }
        codeService.createPartnerCode(savedUser);

        return savedUser;
    }

    public AuthTokensDto signIn(BasicAuthSignInDto dto) {
        User user = userDataService.getUserByEmail(dto.getEmail());
        authValidator.validateSignIn(dto, user.getPassword());
        return generateAuthTokens(user.getId(), user.getRole());
    }

    public AuthTokensDto googleSignIn(GoogleAuthSignInDto dto) {
        var googleUserInfo = oAuthGoogleService.getUserInfo(dto.getAuthorizationCode());
        User user = userDataService.getUserByEmail(googleUserInfo.getEmail());
        return generateAuthTokens(user.getId(), user.getRole());
    }

    public AuthTokensDto refresh(String refreshToken) {
        String userId = jwtTokenUtil.extractSubject(refreshToken);
        User user = userDataService.getUserById(Integer.parseInt(userId));
        return generateAuthTokens(user.getId(), user.getRole());
    }

    public void recovery(RecoveryDto dto) {
        if (dto.getEmail() != null) {
            User user = userDataService.getUserByEmail(dto.getEmail());
            var token = tokenService.saveAndGetPasswordToken(user.getId());
            var messageDto = recoveryMessageBuilder.buildMessage(
                    new RecoveryEmailDto(dto.getEmail(), webRecoveryUrl + "?token=" + token)
            );
            emailService.send(dto.getEmail(), messageDto);
        }
        else {
            if (dto.getToken() == null || dto.getPassword() == null) {
                throw new SecurityException("Token and password must be provided");
            }
            if (!dto.getConfirmPassword().equals(dto.getPassword())) {
                throw new SecurityException("Password and confirm password do not match");
            }
            String userId = tokenService.getAndDeletePasswordToken(dto.getToken());
            if (userId == null) {
                throw new SecurityException("Invalid token");
            }
            User user = userDataService.getUserById(Integer.parseInt(userId));
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
            userDataService.save(user);
        }
    }

    private AuthTokensDto generateAuthTokens(Integer userId, UserRole role) {
        Map<String, Object> claims = Map.of("role", role.toString());
        String savedUserId = userId.toString();
        String accessToken = jwtTokenUtil.generateToken(savedUserId, claims, accessLifetime);
        String refreshToken = jwtTokenUtil.generateToken(savedUserId, claims, refreshLifeTime);
        return new AuthTokensDto(refreshToken, accessToken);
    }
}
