package com.territorial.combat.publicapi;

import com.territorial.auction.global.common.ApiResponse;
import com.territorial.combat.domain.building.dto.HarvestIslandGpResponse;
import com.territorial.combat.domain.building.dto.IslandResponse;
import com.territorial.combat.domain.building.dto.PlaceBuildingRequest;
import com.territorial.combat.domain.building.dto.PlaceBuildingResponse;
import com.territorial.combat.domain.building.dto.ProductionBoostResponse;
import com.territorial.combat.domain.building.service.BuildingService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/island")
@RequiredArgsConstructor
public class IslandController {

    private final BuildingService buildingService;

    @GetMapping
    public ResponseEntity<ApiResponse<IslandResponse>> getIsland(
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(buildingService.getIsland(userId)));
    }

    @GetMapping("/buildings")
    public ResponseEntity<ApiResponse<List<IslandResponse.IslandBuildingInfo>>> getIslandBuildings(
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(buildingService.getIslandBuildings(userId)));
    }

    @PostMapping("/buildings")
    public ResponseEntity<ApiResponse<PlaceBuildingResponse>> placeOnIsland(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody @Valid PlaceBuildingRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(buildingService.placeOnIsland(userId, request)));
    }

    @PostMapping("/harvest")
    public ResponseEntity<ApiResponse<HarvestIslandGpResponse>> harvestGp(
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(buildingService.harvestIslandGp(userId)));
    }

    @PostMapping("/production-boost")
    public ResponseEntity<ApiResponse<ProductionBoostResponse>> activateProductionBoost(
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(buildingService.activateProductionBoost(userId)));
    }
}
