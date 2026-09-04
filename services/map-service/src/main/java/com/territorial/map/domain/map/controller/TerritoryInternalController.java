package com.territorial.map.domain.map.controller;

import com.territorial.map.domain.map.dto.OccupyRequest;
import com.territorial.map.domain.map.dto.ReleaseRequest;
import com.territorial.map.domain.map.dto.TerritoryCombatContextResponse;
import com.territorial.map.domain.map.service.TerritoryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
