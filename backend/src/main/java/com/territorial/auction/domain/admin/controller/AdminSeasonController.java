package com.territorial.auction.domain.admin.controller;

import com.territorial.auction.domain.admin.dto.AdminCreateSeasonRequest;
import com.territorial.auction.domain.admin.dto.AdminSeasonListResponse;
import com.territorial.auction.domain.admin.dto.AdminSeasonResponse;
import com.territorial.auction.domain.admin.service.AdminSeasonService;
import com.territorial.auction.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/seasons")
@RequiredArgsConstructor
public class AdminSeasonController {

    private final AdminSeasonService adminSeasonService;

    @GetMapping
    public ResponseEntity<ApiResponse<AdminSeasonListResponse>> getSeasons() {
        return ResponseEntity.ok(ApiResponse.ok(adminSeasonService.getSeasons()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AdminSeasonResponse>> createSeason(
            @AuthenticationPrincipal Long adminUserId,
            @RequestBody @Valid AdminCreateSeasonRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(adminSeasonService.createSeason(adminUserId, request)));
    }

    @PatchMapping("/{seasonId}/end")
    public ResponseEntity<ApiResponse<AdminSeasonResponse>> endSeason(
            @AuthenticationPrincipal Long adminUserId, @PathVariable Long seasonId) {
        return ResponseEntity.ok(
                ApiResponse.ok(adminSeasonService.endSeason(adminUserId, seasonId)));
    }
}
