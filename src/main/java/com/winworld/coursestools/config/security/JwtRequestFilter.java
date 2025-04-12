package com.winworld.coursestools.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.winworld.coursestools.exception.ErrorResponse;
import com.winworld.coursestools.util.jwt.impl.AuthJwtTokenUtil;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

import static com.winworld.coursestools.config.security.PublicUrlsHolder.PUBLIC_URL_PATTERNS;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtRequestFilter extends OncePerRequestFilter {
    public static final String ROLE_PREFIX = "ROLE_";
    private final AuthJwtTokenUtil jwtTokenUtil;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        Integer userId;

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                userId = Integer.valueOf(jwtTokenUtil.extractSubject(token));
            } catch (ExpiredJwtException e) {
                handleExpiredJwtException(e, response);
                return;
            } catch (SignatureException e) {
                handleSignatureException(e, response);
                return;
            }

            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                String role = jwtTokenUtil.extractClaim(token, "role", String.class);
                UsernamePasswordAuthenticationToken user =
                        new UsernamePasswordAuthenticationToken(
                                new UserPrincipal(userId),
                                null,
                                List.of(new SimpleGrantedAuthority(ROLE_PREFIX + role))
                        );
                SecurityContextHolder.getContext().setAuthentication(user);
            }
        }
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return PUBLIC_URL_PATTERNS.matches(request);
    }

    private void handleExpiredJwtException(ExpiredJwtException e, HttpServletResponse response) {
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.UNAUTHORIZED,
                "Token expired",
                e.getMessage()
        );
        configureResponse(errorResponse, response);
    }

    @SneakyThrows
    private void handleSignatureException(SignatureException e, HttpServletResponse response) {
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.UNAUTHORIZED,
                "Invalid signature",
                e.getMessage()
        );
        configureResponse(errorResponse, response);
    }

    @SneakyThrows
    private void configureResponse(ErrorResponse errorResponse, HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
