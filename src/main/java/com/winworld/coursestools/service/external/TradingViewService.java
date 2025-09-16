package com.winworld.coursestools.service.external;

import com.winworld.coursestools.dto.external.GeoLocationReadDto;
import com.winworld.coursestools.exception.exceptions.EntityNotFoundException;
import com.winworld.coursestools.exception.exceptions.ExternalServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
@Slf4j
public class TradingViewService {
    private static final String TRADING_VIEW_URL = "tradingview.com";
    private final RestTemplate restTemplate;

    public void checkTradingViewName(String name) {
        String url = UriComponentsBuilder.newInstance()
                .scheme("https")
                .host(TRADING_VIEW_URL)
                .path("/u/{name}")
                .buildAndExpand(name)
                .toUriString();

        try {
            restTemplate.getForEntity(
                    url, Void.class
            );
        } catch (HttpClientErrorException.NotFound e) {
            throw new EntityNotFoundException("TradingView user not found");
        } catch (Exception e) {
            log.error("Error while checking TradingView name: {}", e.getMessage());
            throw new ExternalServiceException("Error while checking TradingView name");
        }
    }
}
