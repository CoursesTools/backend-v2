package com.winworld.coursestools.service.external;

import com.winworld.coursestools.config.props.GoogleOAuthProperties;
import com.winworld.coursestools.dto.external.GoogleUserInfoDto;
import com.winworld.coursestools.exception.ExternalServiceException;
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
public class OAuthGoogleService {
    private static final String GET_TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String USER_INFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo";
    private static final String GRANT_TYPE = "authorization_code";

    private final RestTemplate restTemplate;
    private final GoogleOAuthProperties googleOAuthProperties;

    @Retry(name = "default", fallbackMethod = "handleFallback")
    public GoogleUserInfoDto getUserInfo(String authorizationCode) {
        String accessToken = getAccessToken(authorizationCode);
        HttpHeaders headers = getAuthBearerHeader(accessToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<GoogleUserInfoDto> response = restTemplate.exchange(
                USER_INFO_URL,
                HttpMethod.GET,
                entity,
                GoogleUserInfoDto.class
        );
        return response.getBody();
    }

    private String getAccessToken(String authorizationCode) {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                GET_TOKEN_URL,
                HttpMethod.POST,
                getHttpEntityForAccessToken(authorizationCode),
                new ParameterizedTypeReference<>() {
                }
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
        formParams.add("client_id", googleOAuthProperties.clientId());
        formParams.add("client_secret", googleOAuthProperties.clientSecret());
        formParams.add("redirect_uri", googleOAuthProperties.redirectUri());
        formParams.add("grant_type", GRANT_TYPE);

        return new HttpEntity<>(formParams, headers);
    }

    private GoogleUserInfoDto handleFallback(String authorizationCode, Throwable throwable) {
        log.error(
                "Error while handle auth code for google: {}",
                authorizationCode,
                throwable
        );
        throw new ExternalServiceException("Authorization through Google failed");
    }
}
