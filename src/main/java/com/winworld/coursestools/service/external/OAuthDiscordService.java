package com.winworld.coursestools.service.external;

import com.winworld.coursestools.config.props.DiscordOAuthProperties;
import com.winworld.coursestools.dto.external.DiscordUserInfoDto;
import com.winworld.coursestools.dto.external.GoogleUserInfoDto;
import com.winworld.coursestools.exception.exceptions.ExternalServiceException;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OAuthDiscordService {
    private final RestTemplate restTemplate;
    private final DiscordOAuthProperties discordOAuthProperties;
    private static final String GET_TOKEN_URL = "https://discord.com/api/oauth2/token";
    private static final String USER_INFO_URL = "https://discord.com/api/users/@me";
    private static final String GRANT_TYPE = "authorization_code";

    @Retry(name = "default", fallbackMethod = "handleFallback")
    public DiscordUserInfoDto getUserInfo(String authorizationCode) {
        String accessToken = getAccessToken(authorizationCode);
        HttpHeaders headers = getAuthBearerHeader(accessToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<DiscordUserInfoDto> response = restTemplate.exchange(
                USER_INFO_URL,
                HttpMethod.GET,
                entity,
                DiscordUserInfoDto.class
        );
        return response.getBody();
    }

    private String getAccessToken(String authorizationCode) {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                GET_TOKEN_URL,
                HttpMethod.POST,
                getHttpEntityForAccessToken(authorizationCode),
                new ParameterizedTypeReference<>() {}
        );
        Map<String, Object> tokenResponse = response.getBody();
        return (String) tokenResponse.get("access_token");
    }

    private HttpHeaders getAuthBearerHeader(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private HttpEntity<MultiValueMap<String, String>> getHttpEntityForAccessToken(
            String authorizationCode
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> formParams = new LinkedMultiValueMap<>();
        formParams.add("code", authorizationCode);
        formParams.add("client_id", discordOAuthProperties.clientId());
        formParams.add("client_secret", discordOAuthProperties.clientSecret());
        formParams.add("redirect_uri", discordOAuthProperties.redirectUri());
        formParams.add("grant_type", GRANT_TYPE);

        return new HttpEntity<>(formParams, headers);
    }

    private DiscordUserInfoDto handleFallback(String authorizationCode, Throwable throwable) {
        log.error(
                "Error while handle auth code for discord: {}",
                authorizationCode,
                throwable
        );
        throw new ExternalServiceException("Authorization through Discord failed");
    }
}
