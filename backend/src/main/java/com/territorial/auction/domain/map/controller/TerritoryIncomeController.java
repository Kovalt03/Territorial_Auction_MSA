package com.territorial.auction.domain.map.controller;

import com.territorial.auction.domain.map.dto.CollectTerritoryResponse;
import com.territorial.auction.domain.map.service.TerritoryIncomeService;
import com.territorial.auction.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/territories")
@RequiredArgsConstructor
public class TerritoryIncomeController {

    private final TerritoryIncomeService territoryIncomeService;

    @PostMapping("/{id}/collect")
    public ResponseEntity<ApiResponse<CollectTerritoryResponse>> collect(
            @AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(territoryIncomeService.collect(userId, id)));
    }
}
