package com.territorial.auction.domain.military.controller;

import com.territorial.auction.domain.military.dto.ResearchStatusResponse;
import com.territorial.auction.domain.military.dto.StartResearchResponse;
import com.territorial.auction.domain.military.service.ResearchService;
import com.territorial.auction.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/military/research")
@RequiredArgsConstructor
public class ResearchController {

    private final ResearchService researchService;

    @GetMapping
    public ResponseEntity<ApiResponse<ResearchStatusResponse>> getResearch(
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(researchService.getResearch(userId)));
    }

    @PostMapping("/{unitTypeId}")
    public ResponseEntity<ApiResponse<StartResearchResponse>> startResearch(
            @AuthenticationPrincipal Long userId, @PathVariable Long unitTypeId) {
        return ResponseEntity.ok(ApiResponse.ok(researchService.startResearch(userId, unitTypeId)));
    }
}
