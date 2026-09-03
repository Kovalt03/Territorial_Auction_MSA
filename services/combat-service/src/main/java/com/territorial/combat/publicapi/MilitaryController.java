package com.territorial.combat.publicapi;

import com.territorial.auction.global.common.ApiResponse;
import com.territorial.combat.domain.military.dto.*;
import com.territorial.combat.domain.military.service.MilitaryQueryService;
import com.territorial.combat.domain.military.service.MilitaryService;
import com.territorial.combat.domain.military.service.SiegeCommandService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/military")
@RequiredArgsConstructor
public class MilitaryController {

    private final MilitaryService militaryService;
    private final MilitaryQueryService queryService;
    private final SiegeCommandService siegeCommandService;

    @GetMapping("/attack-tokens")
    public ResponseEntity<ApiResponse<AttackTokenResponse>> getAttackTokens(
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(militaryService.getAttackTokens(userId)));
    }

    @GetMapping("/units")
    public ResponseEntity<ApiResponse<UnitListResponse>> getUnitList(
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(militaryService.getUnitList(userId)));
    }

    @GetMapping("/unit-types")
    public ResponseEntity<ApiResponse<List<UnitTypeCatalogResponse>>> getUnitTypeCatalog() {
        return ResponseEntity.ok(ApiResponse.ok(militaryService.getUnitTypeCatalog()));
    }

    @PostMapping("/units")
    public ResponseEntity<ApiResponse<ProduceUnitResponse>> produceUnit(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody @Valid ProduceUnitRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(militaryService.produceUnit(userId, request)));
    }

    @PostMapping("/units/deploy")
    public ResponseEntity<ApiResponse<DeployUnitResponse>> deployUnit(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody @Valid DeployUnitRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(militaryService.deployUnit(userId, request)));
    }

    @PostMapping("/units/recall")
    public ResponseEntity<ApiResponse<RecallUnitResponse>> recallUnit(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody @Valid RecallUnitRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(militaryService.recallUnit(userId, request)));
    }

    @PostMapping("/units/move")
    public ResponseEntity<ApiResponse<MoveUnitResponse>> moveUnit(
            @RequestHeader("X-User-Id") Long userId, @RequestBody @Valid MoveUnitRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(militaryService.moveUnit(userId, request)));
    }

    @GetMapping("/siege/target/{territoryId}")
    public ResponseEntity<ApiResponse<SiegeTargetResponse>> getSiegeTarget(
            @PathVariable Long territoryId) {
        return ResponseEntity.ok(ApiResponse.ok(queryService.getSiegeTarget(territoryId)));
    }

    @PostMapping("/siege")
    public ResponseEntity<ApiResponse<DeclareSiegeResponse>> declareSiege(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody @Valid DeclareSiegeRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(siegeCommandService.declareSiege(userId, request)));
    }

    @GetMapping("/siege/{siegeId}/result")
    public ResponseEntity<ApiResponse<SiegeResultResponse>> getSiegeResult(
            @RequestHeader("X-User-Id") Long userId, @PathVariable Long siegeId) {
        return ResponseEntity.ok(ApiResponse.ok(queryService.getSiegeResult(userId, siegeId)));
    }

    @PostMapping("/scout/{territoryId}")
    public ResponseEntity<ApiResponse<ScoutTerritoryResponse>> scoutTerritory(
            @RequestHeader("X-User-Id") Long userId, @PathVariable Long territoryId) {
        return ResponseEntity.ok(ApiResponse.ok(queryService.scoutTerritory(userId, territoryId)));
    }

    @GetMapping("/territory/{territoryId}/garrison")
    public ResponseEntity<ApiResponse<List<GarrisonUnitResponse>>> getTerritoryGarrison(
            @RequestHeader("X-User-Id") Long userId, @PathVariable Long territoryId) {
        return ResponseEntity.ok(
                ApiResponse.ok(militaryService.getTerritoryGarrison(userId, territoryId)));
    }
}
