package com.territorial.auction.domain.military.controller;

import com.territorial.auction.domain.military.dto.*;
import com.territorial.auction.domain.military.service.MilitaryService;
import com.territorial.auction.global.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/military")
@RequiredArgsConstructor
public class MilitaryController {

    private final MilitaryService militaryService;

    @GetMapping("/attack-tokens")
    public ResponseEntity<ApiResponse<AttackTokenResponse>> getAttackTokens(
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(militaryService.getAttackTokens(userId)));
    }

    @GetMapping("/units")
    public ResponseEntity<ApiResponse<UnitListResponse>> getUnitList(
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(militaryService.getUnitList(userId)));
    }

    @GetMapping("/unit-types")
    public ResponseEntity<ApiResponse<List<UnitTypeCatalogResponse>>> getUnitTypeCatalog() {
        return ResponseEntity.ok(ApiResponse.ok(militaryService.getUnitTypeCatalog()));
    }

    @PostMapping("/units")
    public ResponseEntity<ApiResponse<ProduceUnitResponse>> produceUnit(
            @AuthenticationPrincipal Long userId, @RequestBody @Valid ProduceUnitRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(militaryService.produceUnit(userId, request)));
    }

    @PostMapping("/units/deploy")
    public ResponseEntity<ApiResponse<DeployUnitResponse>> deployUnit(
            @AuthenticationPrincipal Long userId, @RequestBody @Valid DeployUnitRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(militaryService.deployUnit(userId, request)));
    }

    @PostMapping("/units/recall")
    public ResponseEntity<ApiResponse<RecallUnitResponse>> recallUnit(
            @AuthenticationPrincipal Long userId, @RequestBody @Valid RecallUnitRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(militaryService.recallUnit(userId, request)));
    }

    @PostMapping("/units/move")
    public ResponseEntity<ApiResponse<MoveUnitResponse>> moveUnit(
            @AuthenticationPrincipal Long userId, @RequestBody @Valid MoveUnitRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(militaryService.moveUnit(userId, request)));
    }

    @GetMapping("/siege/target/{territoryId}")
    public ResponseEntity<ApiResponse<SiegeTargetResponse>> getSiegeTarget(
            @PathVariable Long territoryId) {
        return ResponseEntity.ok(ApiResponse.ok(militaryService.getSiegeTarget(territoryId)));
    }

    @PostMapping("/siege")
    public ResponseEntity<ApiResponse<DeclareSiegeResponse>> declareSiege(
            @AuthenticationPrincipal Long userId, @RequestBody @Valid DeclareSiegeRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(militaryService.declareSiege(userId, request)));
    }

    @GetMapping("/siege/{siegeId}/result")
    public ResponseEntity<ApiResponse<SiegeResultResponse>> getSiegeResult(
            @AuthenticationPrincipal Long userId, @PathVariable Long siegeId) {
        return ResponseEntity.ok(ApiResponse.ok(militaryService.getSiegeResult(userId, siegeId)));
    }

    @PostMapping("/scout/{territoryId}")
    public ResponseEntity<ApiResponse<ScoutTerritoryResponse>> scoutTerritory(
            @AuthenticationPrincipal Long userId, @PathVariable Long territoryId) {
        return ResponseEntity.ok(
                ApiResponse.ok(militaryService.scoutTerritory(userId, territoryId)));
    }

    @GetMapping("/territory/{territoryId}/garrison")
    public ResponseEntity<ApiResponse<List<GarrisonUnitResponse>>> getTerritoryGarrison(
            @AuthenticationPrincipal Long userId, @PathVariable Long territoryId) {
        return ResponseEntity.ok(
                ApiResponse.ok(militaryService.getTerritoryGarrison(userId, territoryId)));
    }
}
