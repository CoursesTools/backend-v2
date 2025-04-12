package com.winworld.coursestools.service;

import com.winworld.coursestools.dto.auth.AuthSignInDto;
import com.winworld.coursestools.dto.auth.AuthSignUpDto;
import com.winworld.coursestools.dto.auth.AuthTokensDto;
import com.winworld.coursestools.dto.recovery.RecoveryDto;
import com.winworld.coursestools.dto.recovery.RecoveryEmailDto;
import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.entity.user.UserFinance;
import com.winworld.coursestools.entity.user.UserPartnership;
import com.winworld.coursestools.entity.user.UserProfile;
import com.winworld.coursestools.enums.UserRole;
import com.winworld.coursestools.exception.SecurityException;
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
    private final PasswordResetTokenService passwordResetTokenService;
    private final StringGeneratorUtil stringGeneratorUtil;
    private final PromoCodeService promoCodeService;

    @Value("${jwt.refreshLifeTime}")
    private Duration refreshLifeTime;

    @Value("${jwt.accessLifeTime}")
    private Duration accessLifetime;

    @Value("${urls.web-recovery}")
    private String webRecoveryUrl;

    @Transactional
    public AuthTokensDto signup(AuthSignUpDto dto, String forwardedFor) {
        var savedUser = configureUserAndSave(dto);
        eventPublisher.publishEvent(
                userMapper.toEvent(savedUser, forwardedFor)
        );
        return generateAuthTokens(savedUser.getId(), savedUser.getRole());
    }

    @Transactional
    public AuthTokensDto googleSignup(AuthSignUpDto dto, String forwardedFor) {
        var googleUserInfo = oAuthGoogleService.getUserInfo(dto.getAuthorizationCode());
        if (!googleUserInfo.isEmailVerified()) {
            throw new SecurityException("Email not verified by Google");
        }
        dto.setPassword(stringGeneratorUtil.generatePassword());
        dto.setEmail(googleUserInfo.getEmail());
        var savedUser = configureUserAndSave(dto);

        eventPublisher.publishEvent(
                userMapper.toEvent(savedUser, forwardedFor, dto.getPassword())
        );
        return generateAuthTokens(savedUser.getId(), savedUser.getRole());
    }

    public AuthTokensDto signIn(AuthSignInDto dto) {
        User user = userDataService.getUserByEmail(dto.getEmail());
        authValidator.validateSignIn(dto, user.getPassword());
        return generateAuthTokens(user.getId(), user.getRole());
    }

    public AuthTokensDto googleSignIn(AuthSignInDto dto) {
        var googleUserInfo = oAuthGoogleService.getUserInfo(dto.getAuthorizationCode());
        User user = userDataService.getUserByEmail(googleUserInfo.getEmail());
        return generateAuthTokens(user.getId(), user.getRole());
    }

    public AuthTokensDto refresh(String refreshToken) {
        String userId = jwtTokenUtil.extractSubject(refreshToken);
        User user = userDataService.getUserById(Integer.parseInt(userId));
        return generateAuthTokens(user.getId(), user.getRole());
    }

    public void recoveryRequest(String email) {
        User user = userDataService.getUserByEmail(email);
        var token = passwordResetTokenService.saveToken(user.getId());
        var messageDto = recoveryMessageBuilder.buildMessage(
                new RecoveryEmailDto(email, webRecoveryUrl + "?token=" + token)
        );
        emailService.send(email, messageDto);
    }

    public void recovery(RecoveryDto dto) {
        String userId = passwordResetTokenService.getToken(dto.getToken());
        if (userId == null) {
            throw new SecurityException("Invalid token");
        }
        User user = userDataService.getUserById(Integer.parseInt(userId));
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        userDataService.save(user);
    }

    private User configureUserAndSave(AuthSignUpDto dto) {
        authValidator.validateSignUp(dto);
        var user = authMapper.toEntity(dto);
        var userProfile = new UserProfile();
        var userPartnership = new UserPartnership();
        var userFinance = new UserFinance();

        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setRole(UserRole.USER);
        if (dto.getReferrerCode() != null) {
            var referrer = userDataService.getUserByPartnerCode(dto.getReferrerCode());
            user.setReferrer(referrer);
        }
        userPartnership.setLevel(0);

        user.setProfile(userProfile);
        user.setPartnership(userPartnership);
        user.setFinance(userFinance);
        userPartnership.setUser(user);
        userProfile.setUser(user);
        userFinance.setUser(user);

        User savedUser = userDataService.save(user);
        promoCodeService.createPartnerCode(savedUser);
        return savedUser;
    }


    private AuthTokensDto generateAuthTokens(Integer userId, UserRole role) {
        Map<String, Object> claims = Map.of("role", role.toString());
        String savedUserId = userId.toString();
        String accessToken = jwtTokenUtil.generateToken(savedUserId, claims, accessLifetime);
        String refreshToken = jwtTokenUtil.generateToken(savedUserId, claims, refreshLifeTime);
        return new AuthTokensDto(refreshToken, accessToken);
    }
}
