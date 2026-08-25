package com.territorial.auction.domain.building.controller;

import com.territorial.auction.domain.building.dto.HarvestIslandGpResponse;
import com.territorial.auction.domain.building.dto.IslandResponse;
import com.territorial.auction.domain.building.dto.PlaceBuildingRequest;
import com.territorial.auction.domain.building.dto.PlaceBuildingResponse;
import com.territorial.auction.domain.building.dto.ProductionBoostResponse;
import com.territorial.auction.domain.building.service.BuildingService;
import com.territorial.auction.global.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/island")
@RequiredArgsConstructor
public class IslandController {

    private final BuildingService buildingService;

    @GetMapping
    public ResponseEntity<ApiResponse<IslandResponse>> getIsland(
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(buildingService.getIsland(userId)));
    }

    @GetMapping("/buildings")
    public ResponseEntity<ApiResponse<List<IslandResponse.IslandBuildingInfo>>> getIslandBuildings(
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(buildingService.getIslandBuildings(userId)));
    }

    @PostMapping("/buildings")
    public ResponseEntity<ApiResponse<PlaceBuildingResponse>> placeOnIsland(
            @AuthenticationPrincipal Long userId,
            @RequestBody @Valid PlaceBuildingRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(buildingService.placeOnIsland(userId, request)));
    }

    @PostMapping("/harvest")
    public ResponseEntity<ApiResponse<HarvestIslandGpResponse>> harvestGp(
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(buildingService.harvestIslandGp(userId)));
    }

    @PostMapping("/production-boost")
    public ResponseEntity<ApiResponse<ProductionBoostResponse>> activateProductionBoost(
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(buildingService.activateProductionBoost(userId)));
    }
}
