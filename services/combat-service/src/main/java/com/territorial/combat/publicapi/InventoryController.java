package com.territorial.combat.publicapi;

import com.territorial.auction.global.common.ApiResponse;
import com.territorial.combat.domain.building.dto.InventoryResponse;
import com.territorial.combat.domain.building.dto.PlaceFromInventoryRequest;
import com.territorial.combat.domain.building.dto.PlaceFromInventoryResponse;
import com.territorial.combat.domain.building.dto.PlaceOnIslandFromInventoryRequest;
import com.territorial.combat.domain.building.service.BuildingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final BuildingService buildingService;

    @GetMapping
    public ResponseEntity<ApiResponse<InventoryResponse>> getInventory(
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(buildingService.getInventory(userId)));
    }

    @PostMapping("/{inventoryId}/place")
    public ResponseEntity<ApiResponse<PlaceFromInventoryResponse>> placeFromInventory(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long inventoryId,
            @RequestBody @Valid PlaceFromInventoryRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(buildingService.placeFromInventory(userId, inventoryId, request)));
    }

    @PostMapping("/{inventoryId}/place-on-island")
    public ResponseEntity<ApiResponse<PlaceFromInventoryResponse>> placeOnIsland(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long inventoryId,
            @RequestBody @Valid PlaceOnIslandFromInventoryRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(
                        buildingService.placeFromInventoryOnIsland(userId, inventoryId, request)));
    }
}
