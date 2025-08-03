package com.winworld.coursestools.service.external;

import com.winworld.coursestools.config.props.GoogleOAuthProperties;
import com.winworld.coursestools.dto.external.GoogleUserInfoDto;
import com.winworld.coursestools.exception.exceptions.ExternalServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuthGoogleServiceTest {

    private static final String AUTH_CODE = "test_auth_code";
    private static final String ACCESS_TOKEN = "test_access_token";
    private static final String TEST_EMAIL = "test@example.com";
    private static final String GET_TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String USER_INFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo";

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private GoogleOAuthProperties googleOAuthProperties;

    @InjectMocks
    private OAuthGoogleService oAuthGoogleService;

    @BeforeEach
    void setUp() {
        when(googleOAuthProperties.clientId()).thenReturn("test-client-id");
        when(googleOAuthProperties.clientSecret()).thenReturn("test-client-secret");
        when(googleOAuthProperties.redirectUri()).thenReturn("http://test-redirect-uri.com");
    }

    @Test
    void getUserInfo_shouldReturnUserInfo_whenValidAuthorizationCodeProvided() {
        // given
        Map<String, Object> tokenResponse = new HashMap<>();
        tokenResponse.put("access_token", ACCESS_TOKEN);

        ResponseEntity<Map<String, Object>> tokenResponseEntity =
                new ResponseEntity<>(tokenResponse, HttpStatus.OK);

        GoogleUserInfoDto userInfo = new GoogleUserInfoDto(TEST_EMAIL, true);
        ResponseEntity<GoogleUserInfoDto> userInfoResponseEntity =
                new ResponseEntity<>(userInfo, HttpStatus.OK);

        when(restTemplate.exchange(
                eq(GET_TOKEN_URL),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn(tokenResponseEntity);

        when(restTemplate.exchange(
                eq(USER_INFO_URL),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(GoogleUserInfoDto.class)))
                .thenReturn(userInfoResponseEntity);

        // when
        GoogleUserInfoDto result = oAuthGoogleService.getUserInfo(AUTH_CODE);

        // then
        assertEquals(TEST_EMAIL, result.getEmail());
        assertTrue(result.isEmailVerified());

        // Проверяем, что все вызовы REST были сделаны правильно
        verify(restTemplate, times(1)).exchange(
                eq(GET_TOKEN_URL),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class));

        verify(restTemplate, times(1)).exchange(
                eq(USER_INFO_URL),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(GoogleUserInfoDto.class));
    }

    @Test
    void getUserInfo_shouldPassCorrectParameters_whenCallingGoogleAPI() {
        // given
        Map<String, Object> tokenResponse = new HashMap<>();
        tokenResponse.put("access_token", ACCESS_TOKEN);

        ResponseEntity<Map<String, Object>> tokenResponseEntity =
                new ResponseEntity<>(tokenResponse, HttpStatus.OK);

        GoogleUserInfoDto userInfo = new GoogleUserInfoDto(TEST_EMAIL, true);
        ResponseEntity<GoogleUserInfoDto> userInfoResponseEntity =
                new ResponseEntity<>(userInfo, HttpStatus.OK);

        when(restTemplate.exchange(
                anyString(),
                any(HttpMethod.class),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn(tokenResponseEntity);

        when(restTemplate.exchange(
                anyString(),
                any(HttpMethod.class),
                any(HttpEntity.class),
                eq(GoogleUserInfoDto.class)))
                .thenReturn(userInfoResponseEntity);

        // Захватим HTTP entity для проверки параметров
        ArgumentCaptor<HttpEntity<MultiValueMap<String, String>>> entityCaptor =
            ArgumentCaptor.forClass(HttpEntity.class);

        // when
        oAuthGoogleService.getUserInfo(AUTH_CODE);

        // then
        verify(restTemplate, times(1)).exchange(
                eq(GET_TOKEN_URL),
                eq(HttpMethod.POST),
                entityCaptor.capture(),
                any(ParameterizedTypeReference.class));

        HttpEntity<MultiValueMap<String, String>> capturedEntity = entityCaptor.getValue();
        MultiValueMap<String, String> formParams = capturedEntity.getBody();

        assertThat(formParams.getFirst("code")).isEqualTo(AUTH_CODE);
        assertThat(formParams.getFirst("client_id")).isEqualTo("test-client-id");
        assertThat(formParams.getFirst("client_secret")).isEqualTo("test-client-secret");
        assertThat(formParams.getFirst("redirect_uri")).isEqualTo("http://test-redirect-uri.com");
        assertThat(formParams.getFirst("grant_type")).isEqualTo("authorization_code");
    }

    @Test
    void getUserInfo_shouldThrowException_whenAccessTokenNotReceived() {
        // given
        Map<String, Object> tokenResponse = new HashMap<>();
        // Отсутствует access_token

        ResponseEntity<Map<String, Object>> tokenResponseEntity =
                new ResponseEntity<>(tokenResponse, HttpStatus.OK);

        when(restTemplate.exchange(
                eq(GET_TOKEN_URL),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn(tokenResponseEntity);

        // when, then
        assertThrows(NullPointerException.class, () -> oAuthGoogleService.getUserInfo(AUTH_CODE));
    }
}
