package com.winworld.coursestools.config.props;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("oauth2.discord")
public record DiscordOAuthProperties(
        String clientId,
        String clientSecret,
        String redirectUri
) {
}
