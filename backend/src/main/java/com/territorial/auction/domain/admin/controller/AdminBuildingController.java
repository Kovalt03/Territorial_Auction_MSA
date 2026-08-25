package com.territorial.auction.domain.admin.controller;

import com.territorial.auction.domain.admin.dto.AdminCreateBuildingTypeRequest;
import com.territorial.auction.domain.admin.dto.AdminLevelSpecsRequest;
import com.territorial.auction.domain.admin.dto.AdminLevelSpecsRequest.LevelSpecValues;
import com.territorial.auction.domain.admin.dto.AdminUpdateBuildingTypeRequest;
import com.territorial.auction.domain.admin.service.AdminBuildingService;
import com.territorial.auction.domain.building.dto.BuildingTypeCatalogResponse;
import com.territorial.auction.domain.building.dto.BuildingTypeCatalogResponse.BuildingTypeInfo;
import com.territorial.auction.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/building-types")
@RequiredArgsConstructor
public class AdminBuildingController {

    private final AdminBuildingService adminBuildingService;

    @GetMapping
    public ResponseEntity<ApiResponse<BuildingTypeCatalogResponse>> getBuildingTypes() {
        return ResponseEntity.ok(ApiResponse.ok(adminBuildingService.getBuildingTypes()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<BuildingTypeInfo>> create(
            @AuthenticationPrincipal Long adminUserId,
            @RequestBody @Valid AdminCreateBuildingTypeRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(adminBuildingService.create(adminUserId, request)));
    }

    @PatchMapping("/{buildingTypeId}")
    public ResponseEntity<ApiResponse<BuildingTypeInfo>> update(
            @AuthenticationPrincipal Long adminUserId,
            @PathVariable Long buildingTypeId,
            @RequestBody @Valid AdminUpdateBuildingTypeRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(adminBuildingService.update(adminUserId, buildingTypeId, request)));
    }

    @DeleteMapping("/{buildingTypeId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal Long adminUserId, @PathVariable Long buildingTypeId) {
        adminBuildingService.delete(adminUserId, buildingTypeId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/{buildingTypeId}/castle-limits")
    public ResponseEntity<ApiResponse<java.util.Map<Integer, Integer>>> getCastleLimits(
            @PathVariable Long buildingTypeId) {
        return ResponseEntity.ok(
                ApiResponse.ok(adminBuildingService.getCastleLimits(buildingTypeId)));
    }

    @PatchMapping("/{buildingTypeId}/castle-limits")
    public ResponseEntity<ApiResponse<java.util.Map<Integer, Integer>>> updateCastleLimits(
            @AuthenticationPrincipal Long adminUserId,
            @PathVariable Long buildingTypeId,
            @RequestBody
                    com.territorial.auction.domain.admin.dto.AdminCastleLimitsRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(
                        adminBuildingService.updateCastleLimits(
                                adminUserId, buildingTypeId, request.limits())));
    }

    @GetMapping("/{buildingTypeId}/level-specs")
    public ResponseEntity<ApiResponse<java.util.Map<Integer, LevelSpecValues>>> getLevelSpecs(
            @PathVariable Long buildingTypeId) {
        return ResponseEntity.ok(
                ApiResponse.ok(adminBuildingService.getLevelSpecs(buildingTypeId)));
    }

    @PatchMapping("/{buildingTypeId}/level-specs")
    public ResponseEntity<ApiResponse<java.util.Map<Integer, LevelSpecValues>>> updateLevelSpecs(
            @AuthenticationPrincipal Long adminUserId,
            @PathVariable Long buildingTypeId,
            @RequestBody AdminLevelSpecsRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(
                        adminBuildingService.updateLevelSpecs(
                                adminUserId, buildingTypeId, request.specs())));
    }
}
