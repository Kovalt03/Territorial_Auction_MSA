package com.territorial.auction.domain.building.controller;

import com.territorial.auction.domain.building.dto.InventoryResponse;
import com.territorial.auction.domain.building.dto.PlaceFromInventoryRequest;
import com.territorial.auction.domain.building.dto.PlaceFromInventoryResponse;
import com.territorial.auction.domain.building.dto.PlaceOnIslandFromInventoryRequest;
import com.territorial.auction.domain.building.service.BuildingService;
import com.territorial.auction.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final BuildingService buildingService;

    @GetMapping
    public ResponseEntity<ApiResponse<InventoryResponse>> getInventory(
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(buildingService.getInventory(userId)));
    }

    @PostMapping("/{inventoryId}/place")
    public ResponseEntity<ApiResponse<PlaceFromInventoryResponse>> placeFromInventory(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long inventoryId,
            @RequestBody @Valid PlaceFromInventoryRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(buildingService.placeFromInventory(userId, inventoryId, request)));
    }

    @PostMapping("/{inventoryId}/place-on-island")
    public ResponseEntity<ApiResponse<PlaceFromInventoryResponse>> placeOnIsland(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long inventoryId,
            @RequestBody @Valid PlaceOnIslandFromInventoryRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(
                        buildingService.placeFromInventoryOnIsland(userId, inventoryId, request)));
    }
}
