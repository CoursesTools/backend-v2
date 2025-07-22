package com.winworld.coursestools.service;

import com.winworld.coursestools.util.StringGeneratorUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class PasswordResetTokenService {
    private static final String PREFIX = "reset-token:";
    private final RedisTemplate<String, Object> template;
    private final StringGeneratorUtil stringGeneratorUtil;

    @Value("${tokens.password-reset.lifetime}")
    private Duration lifetime;

    public String saveToken(Integer userId) {
        String token = stringGeneratorUtil.generateToken(); //TODO добавить 5 минут
        template.opsForValue().set(PREFIX + token, userId.toString(), lifetime);
        return token;
    }

    public String getToken(String token) {
        return (String) template.opsForValue().getAndDelete(PREFIX + token);
    }
}
