package com.winworld.coursestools.service.external;

import com.winworld.coursestools.dto.external.GeoLocationReadDto;
import com.winworld.coursestools.exception.exceptions.ExternalServiceException;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeoLocationService {
    private static final String IP_API_HOST = "ip-api.com";

    private final RestTemplate restTemplate;

    @Retry(name = "default", fallbackMethod = "handleFallback")
    public String determineUserRegion(String ipAddress) {
        String url = UriComponentsBuilder.newInstance()
                .scheme("http")
                .host(IP_API_HOST)
                .path("/json/{ip}")
                .queryParam("fields", "countryCode")
                .buildAndExpand(ipAddress)
                .toUriString();

        GeoLocationReadDto response = restTemplate.getForObject(
                url, GeoLocationReadDto.class
        );
        if (response == null) {
            throw new ExternalServiceException("Response is null");
        }
        return response.getCountryCode();
    }

    private String handleFallback(String ipAddress, Throwable throwable) {
        log.error(
                "Error while determining user region for IP: {}",
                ipAddress,
                throwable
        );
        throw new ExternalServiceException("Error while determining user region for IP " + ipAddress);
    }
}
