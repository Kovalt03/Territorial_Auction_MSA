package com.territorial.auction.domain.admin.controller;

import com.territorial.auction.domain.admin.dto.AdminBulkResultResponse;
import com.territorial.auction.domain.admin.dto.AdminContinentCompositionResponse;
import com.territorial.auction.domain.admin.dto.AdminContinentCompositionResponse.ContinentComposition;
import com.territorial.auction.domain.admin.dto.AdminGradeDistributionRequest;
import com.territorial.auction.domain.admin.dto.AdminToggleAuctionRequest;
import com.territorial.auction.domain.admin.service.AdminContinentService;
import com.territorial.auction.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/continents")
@RequiredArgsConstructor
public class AdminContinentController {

    private final AdminContinentService adminContinentService;

    @GetMapping
    public ResponseEntity<ApiResponse<AdminContinentCompositionResponse>> getCompositions() {
        return ResponseEntity.ok(ApiResponse.ok(adminContinentService.getCompositions()));
    }

    @PatchMapping("/{continentId}/grade-distribution")
    public ResponseEntity<ApiResponse<ContinentComposition>> applyGradeDistribution(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long continentId,
            @RequestBody @Valid AdminGradeDistributionRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(
                        adminContinentService.applyGradeDistribution(
                                userId, continentId, request)));
    }

    @PatchMapping("/{continentId}/auction")
    public ResponseEntity<ApiResponse<AdminBulkResultResponse>> changeContinentAuction(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long continentId,
            @RequestBody @Valid AdminToggleAuctionRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(
                        adminContinentService.changeContinentAuction(
                                userId, continentId, request)));
    }
}
