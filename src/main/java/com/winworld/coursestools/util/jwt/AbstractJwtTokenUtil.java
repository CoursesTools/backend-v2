package com.winworld.coursestools.util.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.MacAlgorithm;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.Map;

public abstract class AbstractJwtTokenUtil {
    private final SecretKey key;
    private final MacAlgorithm signature;

    public AbstractJwtTokenUtil(String secret, MacAlgorithm signature) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.signature = signature;
    }

    public final String generateToken(
            String subject,
            Map<String, Object> claims,
            Duration lifetime
    ) {
        Date issuedDate = new Date();
        Date expiredDate = new Date(issuedDate.getTime() + lifetime.toMillis());

        return Jwts.builder()
                .subject(subject)
                .claims(claims)
                .issuedAt(issuedDate)
                .expiration(expiredDate)
                .signWith(key, signature)
                .compact();
    }

    public final String extractSubject(String token) {
        return extractAllClaims(token).getSubject();
    }

    public final <T> T extractClaim(String token, String claim, Class<T> type) {
        return extractAllClaims(token).get(claim, type);
    }

    private final Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
