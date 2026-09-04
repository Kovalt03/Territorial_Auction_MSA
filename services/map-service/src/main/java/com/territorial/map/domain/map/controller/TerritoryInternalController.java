package com.territorial.map.domain.map.controller;

import com.territorial.map.domain.map.dto.OccupyRequest;
import com.territorial.map.domain.map.dto.ReleaseRequest;
import com.territorial.map.domain.map.dto.TerritoryCombatContextResponse;
import com.territorial.map.domain.map.service.TerritoryService;
import com.territorial.map.internal.dto.InternalTerritoryRequests;
import com.territorial.map.internal.dto.OwnerHoldingPage;
import com.territorial.map.internal.dto.OwnerTerritoryCount;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/territories")
@RequiredArgsConstructor
public class TerritoryInternalController {
    private final TerritoryService territoryService;

    @GetMapping("/{id}/combat-context")
    public ResponseEntity<TerritoryCombatContextResponse> getCombatContext(@PathVariable Long id) {
        return ResponseEntity.ok(territoryService.getCombatContext(id));
    }

    @GetMapping("/owners/{userId}/combat-contexts")
    public ResponseEntity<List<TerritoryCombatContextResponse>> getOwnedCombatContexts(
            @PathVariable Long userId) {
        return ResponseEntity.ok(territoryService.getOwnedCombatContexts(userId));
    }

    @PostMapping("/{id}/occupy")
    public ResponseEntity<Void> occupy(@PathVariable Long id, @RequestBody OccupyRequest request) {
        territoryService.occupy(
                id, request.winnerId(), request.occupiedUntil(), request.protectedUntil());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/release")
    public ResponseEntity<Void> release(
            @PathVariable Long id, @RequestBody ReleaseRequest request) {
        territoryService.release(id, request.nextAuctionAt());
        return ResponseEntity.ok().build();
    }

    // 공성 인계 — combat-service가 성 파괴 후 소유권 이전을 위임한다.
    @PostMapping("/{id}/takeover")
    public ResponseEntity<Void> takeOver(
            @PathVariable Long id, @RequestBody InternalTerritoryRequests.Takeover request) {
        territoryService.takeOverFromSiege(id, request.newOwnerId(), request.formerOwnerId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/exists")
    public ResponseEntity<Boolean> exists(@PathVariable Long id) {
        return ResponseEntity.ok(territoryService.exists(id));
    }

    @GetMapping("/owners/{userId}/count")
    public ResponseEntity<Long> getOwnerCount(@PathVariable Long userId) {
        return ResponseEntity.ok(territoryService.getOwnerCount(userId));
    }

    @PostMapping("/owner-counts")
    public ResponseEntity<List<OwnerTerritoryCount>> getOwnerCounts(
            @RequestBody InternalTerritoryRequests.OwnerIds request) {
        return ResponseEntity.ok(territoryService.getOwnerCounts(request.userIds()));
    }

    @GetMapping("/owners/{userId}/holdings")
    public ResponseEntity<OwnerHoldingPage> getOwnerHoldings(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(territoryService.getOwnerHoldings(userId, page, size));
    }

    @GetMapping("/owners/{userId}/ids")
    public ResponseEntity<List<Long>> getOwnerTerritoryIds(@PathVariable Long userId) {
        return ResponseEntity.ok(territoryService.getOwnerTerritoryIds(userId));
    }
}
