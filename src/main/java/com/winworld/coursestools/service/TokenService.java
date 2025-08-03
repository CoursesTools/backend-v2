package com.winworld.coursestools.service;

import com.winworld.coursestools.exception.exceptions.SecurityException;
import com.winworld.coursestools.util.StringGeneratorUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class TokenService {
    public static final String PASSWORD_RESET_PREFIX = "reset-token:";
    public static final String EMAIL_CODE_PREFIX = "email-code:";
    public static final String PASSWORD_RESET_COOLDOWN_PREFIX = "reset-cooldown:";
    public static final String TELEGRAM_TOKEN_PREFIX = "telegram-token:";

    private final RedisTemplate<String, Object> template;
    private final StringGeneratorUtil stringGeneratorUtil;

    @Value("${tokens.password-reset.lifetime}")
    private Duration passwordTokenLifetime;

    @Value("${tokens.email-verification.lifetime}")
    private Duration emailVerificationLifetime;

    @Value("${tokens.telegram-binding.lifetime}")
    private Duration telegramBindingLifetime;

    public String saveAndGetPasswordToken(Integer userId) {
        String cooldownKey = PASSWORD_RESET_COOLDOWN_PREFIX + userId;
        Boolean keyExists = template.hasKey(cooldownKey);
        if (Boolean.TRUE.equals(keyExists)) {
            Long ttl = template.getExpire(cooldownKey, TimeUnit.SECONDS);
            throw new SecurityException("You have to wait " + ttl + " seconds before requesting a new password reset token.");
        }
        String token = stringGeneratorUtil.generateToken();
        template.opsForValue().set(PASSWORD_RESET_PREFIX + token, userId.toString(), passwordTokenLifetime);
        template.opsForValue().set(cooldownKey, "1", passwordTokenLifetime);
        return token;
    }

    public String getAndDeletePasswordToken(String token) {
        return (String) template.opsForValue().getAndDelete(PASSWORD_RESET_PREFIX + token);
    }

    public String saveAndGetEmailToken(Integer userId) {
        String token = stringGeneratorUtil.generateToken();
        template.opsForValue().set(EMAIL_CODE_PREFIX + userId, token, emailVerificationLifetime);
        return token;
    }

    public String getEmailToken(Integer userId) {
        return (String) template.opsForValue().get(EMAIL_CODE_PREFIX + userId);
    }

    public void deleteEmailToken(Integer userId) {
        template.delete(EMAIL_CODE_PREFIX + userId);
    }

    public String saveAndGetTelegramToken(Integer userId) {
        String token = stringGeneratorUtil.generateToken();
        template.opsForValue().set(TELEGRAM_TOKEN_PREFIX + token, userId, telegramBindingLifetime);
        return token;
    }

    public Integer getAndDeleteTelegramToken(String token) {
        return (Integer) template.opsForValue().getAndDelete(TELEGRAM_TOKEN_PREFIX + token);
    }
}