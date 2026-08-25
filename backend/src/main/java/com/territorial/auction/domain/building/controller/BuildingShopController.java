package com.territorial.auction.domain.building.controller;

import com.territorial.auction.domain.building.dto.BuildingTypeCatalogResponse;
import com.territorial.auction.domain.building.dto.PurchaseDecorationResponse;
import com.territorial.auction.domain.building.service.BuildingShopService;
import com.territorial.auction.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/building-shop")
@RequiredArgsConstructor
public class BuildingShopController {

    private final BuildingShopService buildingShopService;

    @GetMapping
    public ResponseEntity<ApiResponse<BuildingTypeCatalogResponse>> getShop() {
        return ResponseEntity.ok(ApiResponse.ok(buildingShopService.getShop()));
    }

    @PostMapping("/{buildingTypeId}/purchase")
    public ResponseEntity<ApiResponse<PurchaseDecorationResponse>> purchase(
            @AuthenticationPrincipal Long userId, @PathVariable Long buildingTypeId) {
        return ResponseEntity.ok(
                ApiResponse.ok(buildingShopService.purchase(userId, buildingTypeId)));
    }
}
