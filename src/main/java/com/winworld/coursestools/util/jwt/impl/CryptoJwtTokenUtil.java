package com.winworld.coursestools.util.jwt.impl;

import com.winworld.coursestools.util.jwt.AbstractJwtTokenUtil;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CryptoJwtTokenUtil extends AbstractJwtTokenUtil {
    public CryptoJwtTokenUtil(
            @Value("${payment-platforms.crypto.secret}") String secret
    ) {
        super(secret, Jwts.SIG.HS256);
    }
}
