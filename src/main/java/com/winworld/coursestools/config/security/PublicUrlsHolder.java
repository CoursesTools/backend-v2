package com.winworld.coursestools.config.security;

import lombok.Getter;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Getter
public class PublicUrlsHolder {
    public final static RequestMatcher PUBLIC_URL_PATTERNS;

    static {
        PUBLIC_URL_PATTERNS = new OrRequestMatcher(
                requestMatcher("/v1/authorization/**"),
                requestMatcher("/v1/partnerships/levels", HttpMethod.GET),
                requestMatcher("/actuator/**", HttpMethod.GET),
                requestMatcher("/v1/users/socials/telegram/bind", HttpMethod.PATCH),
                requestMatcher("/v1/payments/crypto"),
                requestMatcher("/v1/payments/stripe"),
                requestMatcher("/v1/payments/payeer"),
                requestMatcher("/swagger-ui/**"),
                requestMatcher("/api-docs.yaml"),
                requestMatcher("/api-docs"),
                requestMatcher("/v1/subscriptions/*", HttpMethod.GET, false)
        );
    }

    private static AntPathRequestMatcher requestMatcher(String pattern, HttpMethod method, boolean caseSensitive) {
        return new AntPathRequestMatcher(pattern, method.name(), caseSensitive);
    }

    private static AntPathRequestMatcher requestMatcher(String pattern, HttpMethod method) {
        return new AntPathRequestMatcher(pattern, method.name());
    }

    private static AntPathRequestMatcher requestMatcher(String pattern) {
        return new AntPathRequestMatcher(pattern);
    }
}
