package com.winworld.coursestools.util.jwt.impl;

import com.winworld.coursestools.util.jwt.AbstractJwtTokenUtil;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AuthJwtTokenUtil extends AbstractJwtTokenUtil {
    public AuthJwtTokenUtil(
            @Value("${jwt.secret}") String secret
    ) {
        super(secret, Jwts.SIG.HS512);
    }
}
