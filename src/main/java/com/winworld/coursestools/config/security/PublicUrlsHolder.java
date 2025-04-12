package com.winworld.coursestools.config.security;

import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.Arrays;
import java.util.List;

@Component
public class PublicUrlsHolder {

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final List<String> publicUrlPatterns;

    public PublicUrlsHolder() {
        this.publicUrlPatterns = Arrays.asList(getPublicUrlPatterns());
    }

    public String[] getPublicUrlPatterns() {
        return new String[]{
                "/v1/authorization/**",
                "/v1/payments/**",
                "/v3/api-docs/**",
                "/swagger-ui/**",
                "/api-docs/**"
        };
    }

    public boolean matches(String requestPath) {
        return publicUrlPatterns.stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, requestPath));
    }
}