package com.winworld.coursestools.controller;

import com.winworld.coursestools.config.security.UserPrincipal;
import com.winworld.coursestools.dto.code.CodeReadDto;
import com.winworld.coursestools.dto.code.PromoCodeCreateDto;
import com.winworld.coursestools.service.CodeService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/codes")
public class CodeController {
    private final CodeService codeService;

    public CodeController(CodeService codeService) {
        this.codeService = codeService;
    }

    @GetMapping("/{code}/pre-check")
    public CodeReadDto preCheckCode(
            @AuthenticationPrincipal UserPrincipal userPrincipal, @PathVariable String code
    ) {
        return codeService.checkCode(userPrincipal.userId(), code);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public CodeReadDto createPromoCode(@RequestBody @Valid PromoCodeCreateDto createDto) {
        return codeService.createPromoCode(createDto);
    }
}
