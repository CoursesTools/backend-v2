package com.winworld.coursestools.config.props;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("oauth2.google")
public record GoogleOAuthProperties(
        String clientId,
        String clientSecret,
        String redirectUri
) {
}
