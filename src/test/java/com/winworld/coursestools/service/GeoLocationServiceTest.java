package com.winworld.coursestools.service;

import com.winworld.coursestools.dto.external.GeoLocationReadDto;
import com.winworld.coursestools.exception.exceptions.ExternalServiceException;
import com.winworld.coursestools.service.external.GeoLocationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeoLocationServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private GeoLocationService geoLocationService;

    private final String testIp = "8.8.8.8";

    @Test
    void determineUserRegion_WithValidResponse_ReturnsCountryCode() {
        // Подготовка
        GeoLocationReadDto mockResponse = new GeoLocationReadDto();
        mockResponse.setCountryCode("US");
        
        when(restTemplate.getForObject(contains(testIp), eq(GeoLocationReadDto.class)))
                .thenReturn(mockResponse);

        // Действие
        String result = geoLocationService.determineUserRegion(testIp);

        // Проверка
        assertEquals("US", result);
    }

    @Test
    void determineUserRegion_WithNullResponse_ThrowsException() {
        // Подготовка
        when(restTemplate.getForObject(anyString(), eq(GeoLocationReadDto.class)))
                .thenReturn(null);

        // Действие и проверка
        assertThrows(ExternalServiceException.class, () -> 
            geoLocationService.determineUserRegion(testIp)
        );
    }
}