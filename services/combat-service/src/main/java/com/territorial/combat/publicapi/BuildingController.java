package com.territorial.combat.publicapi;

import com.territorial.auction.global.common.ApiResponse;
import com.territorial.combat.domain.building.dto.BuildingTypeCatalogResponse;
import com.territorial.combat.domain.building.dto.MoveBuildingRequest;
import com.territorial.combat.domain.building.dto.MoveBuildingResponse;
import com.territorial.combat.domain.building.dto.PlaceBuildingRequest;
import com.territorial.combat.domain.building.dto.PlaceBuildingResponse;
import com.territorial.combat.domain.building.dto.RepairAllRequest;
import com.territorial.combat.domain.building.dto.RepairAllResponse;
import com.territorial.combat.domain.building.dto.RepairBuildingResponse;
import com.territorial.combat.domain.building.dto.RushConstructionResponse;
import com.territorial.combat.domain.building.dto.StoreBuildingResponse;
import com.territorial.combat.domain.building.dto.TerritoryBuildingResponse;
import com.territorial.combat.domain.building.dto.UpgradeBuildingResponse;
import com.territorial.combat.domain.building.service.BuildingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BuildingController {

    private final BuildingService buildingService;

    @GetMapping("/api/v1/building-types")
    public ResponseEntity<ApiResponse<BuildingTypeCatalogResponse>> getBuildingTypes() {
        return ResponseEntity.ok(ApiResponse.ok(buildingService.getBuildingTypes()));
    }

    @GetMapping("/api/v1/map/territories/{territoryId}/buildings")
    public ResponseEntity<ApiResponse<TerritoryBuildingResponse>> getTerritoryBuildings(
            @PathVariable Long territoryId) {
        return ResponseEntity.ok(
                ApiResponse.ok(buildingService.findTerritoryBuildings(territoryId)));
    }

    @PostMapping("/api/v1/map/territories/{territoryId}/buildings")
    public ResponseEntity<ApiResponse<PlaceBuildingResponse>> placeOnTerritory(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long territoryId,
            @RequestBody @Valid PlaceBuildingRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(buildingService.placeOnTerritory(userId, territoryId, request)));
    }

    @PostMapping("/api/v1/buildings/{buildingId}/upgrade")
    public ResponseEntity<ApiResponse<UpgradeBuildingResponse>> upgrade(
            @RequestHeader("X-User-Id") Long userId, @PathVariable Long buildingId) {
        return ResponseEntity.ok(ApiResponse.ok(buildingService.upgrade(userId, buildingId)));
    }

    @PostMapping("/api/v1/buildings/{buildingId}/repair")
    public ResponseEntity<ApiResponse<RepairBuildingResponse>> repair(
            @RequestHeader("X-User-Id") Long userId, @PathVariable Long buildingId) {
        return ResponseEntity.ok(ApiResponse.ok(buildingService.repair(userId, buildingId)));
    }

    @PostMapping("/api/v1/buildings/repair-all")
    public ResponseEntity<ApiResponse<RepairAllResponse>> repairAll(
            @RequestHeader("X-User-Id") Long userId, @RequestBody @Valid RepairAllRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(
                        buildingService.repairAll(
                                userId, request.locationType(), request.locationId())));
    }

    @PostMapping("/api/v1/buildings/{buildingId}/rush")
    public ResponseEntity<ApiResponse<RushConstructionResponse>> rush(
            @RequestHeader("X-User-Id") Long userId, @PathVariable Long buildingId) {
        return ResponseEntity.ok(
                ApiResponse.ok(buildingService.rushConstruction(userId, buildingId)));
    }

    @PatchMapping("/api/v1/buildings/{buildingId}/move")
    public ResponseEntity<ApiResponse<MoveBuildingResponse>> move(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long buildingId,
            @RequestBody @Valid MoveBuildingRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(buildingService.move(userId, buildingId, request)));
    }

    @PostMapping("/api/v1/buildings/{buildingId}/store")
    public ResponseEntity<ApiResponse<StoreBuildingResponse>> store(
            @RequestHeader("X-User-Id") Long userId, @PathVariable Long buildingId) {
        return ResponseEntity.ok(ApiResponse.ok(buildingService.store(userId, buildingId)));
    }
}
