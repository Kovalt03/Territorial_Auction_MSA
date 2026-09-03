package com.territorial.combat.internal.resource;

import com.territorial.combat.internal.resource.CombatResourceContract.*;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/combat")
@RequiredArgsConstructor
public class CombatResourceController {

    private final CombatResourceService resourceService;

    @GetMapping("/users/{userId}/summary")
    public ResponseEntity<UserSummary> getUserSummary(@PathVariable Long userId) {
        return ResponseEntity.ok(resourceService.getUserSummary(userId));
    }

    @GetMapping("/territories/unit-counts")
    public ResponseEntity<List<TerritoryUnitCount>> getTerritoryUnitCounts(
            @RequestParam List<Long> territoryIds) {
        return ResponseEntity.ok(resourceService.getTerritoryUnitCounts(territoryIds));
    }

    @GetMapping("/territories/{territoryId}/storage")
    public ResponseEntity<TerritoryStorageView> getTerritoryStorage(
            @PathVariable Long territoryId) {
        return ResponseEntity.ok(resourceService.getTerritoryStorage(territoryId));
    }

    @PostMapping("/resources/gp-credits")
    public ResponseEntity<GpBalanceResponse> creditGp(@RequestBody @Valid CreditGpRequest request) {
        return ResponseEntity.ok(resourceService.creditGp(request));
    }

    @PostMapping("/resources/attack-token-credits")
    public ResponseEntity<AttackTokenBalanceResponse> creditAttackTokens(
            @RequestBody @Valid CreditAttackTokensRequest request) {
        return ResponseEntity.ok(resourceService.creditAttackTokens(request));
    }

    @PostMapping("/resources/tax-charges")
    public ResponseEntity<ChargeTaxResponse> chargeTax(
            @RequestBody @Valid ChargeTaxRequest request) {
        return ResponseEntity.ok(resourceService.chargeTax(request));
    }

    @PostMapping("/territories/{territoryId}/income-credits")
    public ResponseEntity<CreditIncomeResponse> creditIncome(
            @PathVariable Long territoryId, @RequestBody @Valid CreditIncomeRequest request) {
        return ResponseEntity.ok(resourceService.creditIncome(territoryId, request));
    }
}
