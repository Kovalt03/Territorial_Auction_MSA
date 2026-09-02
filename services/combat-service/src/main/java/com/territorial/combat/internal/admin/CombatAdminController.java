package com.territorial.combat.internal.admin;

import com.territorial.combat.internal.admin.CombatAdminContract.AdjustGpRequest;
import com.territorial.combat.internal.admin.CombatAdminContract.BuildingTypeCatalog;
import com.territorial.combat.internal.admin.CombatAdminContract.BuildingTypeView;
import com.territorial.combat.internal.admin.CombatAdminContract.CastleLimitsRequest;
import com.territorial.combat.internal.admin.CombatAdminContract.CreateBuildingTypeRequest;
import com.territorial.combat.internal.admin.CombatAdminContract.GpBalanceView;
import com.territorial.combat.internal.admin.CombatAdminContract.LevelSpecValues;
import com.territorial.combat.internal.admin.CombatAdminContract.LevelSpecsRequest;
import com.territorial.combat.internal.admin.CombatAdminContract.UnitLevelSpecsRequest;
import com.territorial.combat.internal.admin.CombatAdminContract.UnitLevelValues;
import com.territorial.combat.internal.admin.CombatAdminContract.UnitTypeView;
import com.territorial.combat.internal.admin.CombatAdminContract.UpdateBuildingTypeRequest;
import com.territorial.combat.internal.admin.CombatAdminContract.UpdateUnitTypeRequest;
import com.territorial.combat.internal.admin.CombatAdminContract.UserResourceView;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/admin/combat")
@RequiredArgsConstructor
public class CombatAdminController {

    private final CombatAdminService service;

    @GetMapping("/building-types")
    BuildingTypeCatalog getBuildingTypes() {
        return service.getBuildingTypes();
    }

    @PostMapping("/building-types")
    BuildingTypeView createBuildingType(@RequestBody @Valid CreateBuildingTypeRequest request) {
        return service.createBuildingType(request);
    }

    @PatchMapping("/building-types/{id}")
    BuildingTypeView updateBuildingType(
            @PathVariable Long id, @RequestBody @Valid UpdateBuildingTypeRequest request) {
        return service.updateBuildingType(id, request);
    }

    @DeleteMapping("/building-types/{id}")
    String deleteBuildingType(@PathVariable Long id) {
        return service.deleteBuildingType(id);
    }

    @GetMapping("/building-types/{id}/level-specs")
    Map<Integer, LevelSpecValues> getBuildingLevelSpecs(@PathVariable Long id) {
        return service.getBuildingLevelSpecs(id);
    }

    @PatchMapping("/building-types/{id}/level-specs")
    Map<Integer, LevelSpecValues> updateBuildingLevelSpecs(
            @PathVariable Long id, @RequestBody LevelSpecsRequest request) {
        return service.updateBuildingLevelSpecs(id, request.specs());
    }

    @GetMapping("/building-types/{id}/castle-limits")
    Map<Integer, Integer> getCastleLimits(@PathVariable Long id) {
        return service.getCastleLimits(id);
    }

    @PatchMapping("/building-types/{id}/castle-limits")
    Map<Integer, Integer> updateCastleLimits(
            @PathVariable Long id, @RequestBody CastleLimitsRequest request) {
        return service.updateCastleLimits(id, request.limits());
    }

    @GetMapping("/unit-types")
    List<UnitTypeView> getUnitTypes() {
        return service.getUnitTypes();
    }

    @PatchMapping("/unit-types/{id}")
    UnitTypeView updateUnitType(
            @PathVariable Long id, @RequestBody @Valid UpdateUnitTypeRequest request) {
        return service.updateUnitType(id, request);
    }

    @GetMapping("/unit-types/{id}/level-specs")
    Map<Integer, UnitLevelValues> getUnitLevelSpecs(@PathVariable Long id) {
        return service.getUnitLevelSpecs(id);
    }

    @PatchMapping("/unit-types/{id}/level-specs")
    Map<Integer, UnitLevelValues> updateUnitLevelSpecs(
            @PathVariable Long id, @RequestBody UnitLevelSpecsRequest request) {
        return service.updateUnitLevelSpecs(id, request.specs());
    }

    @GetMapping("/resources/total-gp")
    long getTotalStoredGp() {
        return service.getTotalStoredGp();
    }

    @GetMapping("/users/{userId}/resources")
    UserResourceView getUserResources(
            @PathVariable Long userId, @RequestParam(required = false) List<Long> territoryIds) {
        return service.getUserResources(userId, territoryIds != null ? territoryIds : List.of());
    }

    @PostMapping("/resources/gp-adjustments")
    GpBalanceView adjustGp(@RequestBody @Valid AdjustGpRequest request) {
        return service.adjustGp(request);
    }
}
