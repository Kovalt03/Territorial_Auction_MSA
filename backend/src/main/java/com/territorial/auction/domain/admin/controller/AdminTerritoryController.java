package com.territorial.auction.domain.admin.controller;

import com.territorial.auction.domain.admin.dto.AdminBulkForceStartRequest;
import com.territorial.auction.domain.admin.dto.AdminBulkGradeRequest;
import com.territorial.auction.domain.admin.dto.AdminBulkResultResponse;
import com.territorial.auction.domain.admin.dto.AdminBulkTerritoryAuctionRequest;
import com.territorial.auction.domain.admin.dto.AdminChangeGradeRequest;
import com.territorial.auction.domain.admin.dto.AdminTerritoryListResponse;
import com.territorial.auction.domain.admin.dto.AdminTerritoryResponse;
import com.territorial.auction.domain.admin.dto.AdminToggleAuctionRequest;
import com.territorial.auction.domain.admin.service.AdminTerritoryService;
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
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminTerritoryController {

    private final AdminTerritoryService adminTerritoryService;

    @GetMapping("/continents/{continentId}/territories")
    public ResponseEntity<ApiResponse<AdminTerritoryListResponse>> getTerritories(
            @PathVariable Long continentId) {
        return ResponseEntity.ok(ApiResponse.ok(adminTerritoryService.getTerritories(continentId)));
    }

    @PatchMapping("/territories/{territoryId}/grade")
    public ResponseEntity<ApiResponse<AdminTerritoryResponse>> changeGrade(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long territoryId,
            @RequestBody @Valid AdminChangeGradeRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(adminTerritoryService.changeGrade(userId, territoryId, request)));
    }

    @PatchMapping("/territories/{territoryId}/auction")
    public ResponseEntity<ApiResponse<AdminTerritoryResponse>> changeAuctionEnabled(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long territoryId,
            @RequestBody @Valid AdminToggleAuctionRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(
                        adminTerritoryService.changeAuctionEnabled(userId, territoryId, request)));
    }

    @PostMapping("/territories/{territoryId}/auction/force-start")
    public ResponseEntity<ApiResponse<AdminTerritoryResponse>> forceStartAuction(
            @AuthenticationPrincipal Long userId, @PathVariable Long territoryId) {
        return ResponseEntity.ok(
                ApiResponse.ok(adminTerritoryService.forceStartAuction(userId, territoryId)));
    }

    @PatchMapping("/territories/bulk/grade")
    public ResponseEntity<ApiResponse<AdminBulkResultResponse>> bulkChangeGrade(
            @AuthenticationPrincipal Long userId,
            @RequestBody @Valid AdminBulkGradeRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(adminTerritoryService.bulkChangeGrade(userId, request)));
    }

    @PatchMapping("/territories/bulk/auction")
    public ResponseEntity<ApiResponse<AdminBulkResultResponse>> bulkChangeAuction(
            @AuthenticationPrincipal Long userId,
            @RequestBody @Valid AdminBulkTerritoryAuctionRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(adminTerritoryService.bulkChangeAuction(userId, request)));
    }

    @PostMapping("/territories/bulk/force-start")
    public ResponseEntity<ApiResponse<AdminBulkResultResponse>> bulkForceStart(
            @AuthenticationPrincipal Long userId,
            @RequestBody @Valid AdminBulkForceStartRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(adminTerritoryService.bulkForceStart(userId, request)));
    }
}
