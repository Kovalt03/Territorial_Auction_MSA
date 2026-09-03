package com.territorial.combat.publicapi;

import com.territorial.auction.global.common.ApiResponse;
import com.territorial.combat.domain.military.dto.ResearchStatusResponse;
import com.territorial.combat.domain.military.dto.StartResearchResponse;
import com.territorial.combat.domain.military.service.ResearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/military/research")
@RequiredArgsConstructor
public class ResearchController {

    private final ResearchService researchService;

    @GetMapping
    public ResponseEntity<ApiResponse<ResearchStatusResponse>> getResearch(
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(researchService.getResearch(userId)));
    }

    @PostMapping("/{unitTypeId}")
    public ResponseEntity<ApiResponse<StartResearchResponse>> startResearch(
            @RequestHeader("X-User-Id") Long userId, @PathVariable Long unitTypeId) {
        return ResponseEntity.ok(ApiResponse.ok(researchService.startResearch(userId, unitTypeId)));
    }
}
