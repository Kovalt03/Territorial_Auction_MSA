package com.territorial.map.domain.map.controller;

import com.territorial.auction.global.common.ApiResponse;
import com.territorial.map.domain.map.dto.CollectTerritoryResponse;
import com.territorial.map.domain.map.service.TerritoryIncomeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/territories")
@RequiredArgsConstructor
public class TerritoryIncomeController {

    private final TerritoryIncomeService territoryIncomeService;

    @PostMapping("/{id}/collect")
    public ResponseEntity<ApiResponse<CollectTerritoryResponse>> collect(
            @RequestHeader("X-User-Id") Long userId, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(territoryIncomeService.collect(userId, id)));
    }
}
