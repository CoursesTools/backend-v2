package com.winworld.coursestools.controller;

import com.winworld.coursestools.config.security.UserPrincipal;
import com.winworld.coursestools.dto.order.CreateOrderDto;
import com.winworld.coursestools.dto.order.ReadOrderDto;
import com.winworld.coursestools.facade.OrderFacade;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderFacade orderFacade;

    @PostMapping
    public ReadOrderDto createOrder(
            @RequestBody @Valid CreateOrderDto createDto,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return orderFacade.createOrder(principal.userId(), createDto);
    }
}
