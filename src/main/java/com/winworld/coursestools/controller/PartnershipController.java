package com.winworld.coursestools.controller;

import com.winworld.coursestools.dto.partnership.LevelReadDto;
import com.winworld.coursestools.service.PartnershipService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/partnerships")
@RequiredArgsConstructor
public class PartnershipController {
    private final PartnershipService partnershipService;

    @GetMapping("/levels")
    public List<LevelReadDto> getPartnershipLevels() {
        return partnershipService.getPartnershipLevels();
    }
}
