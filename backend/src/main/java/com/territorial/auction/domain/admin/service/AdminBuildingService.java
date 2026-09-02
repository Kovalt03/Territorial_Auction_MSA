package com.territorial.auction.domain.admin.service;

import com.territorial.auction.domain.admin.client.CombatAdminClient;
import com.territorial.auction.domain.admin.dto.AdminCreateBuildingTypeRequest;
import com.territorial.auction.domain.admin.dto.AdminLevelSpecsRequest.LevelSpecValues;
import com.territorial.auction.domain.admin.dto.AdminUpdateBuildingTypeRequest;
import com.territorial.auction.domain.building.dto.BuildingTypeCatalogResponse;
import com.territorial.auction.domain.building.dto.BuildingTypeCatalogResponse.BuildingTypeInfo;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminBuildingService {
    private final CombatAdminClient combatAdminClient;
    private final AdminAuditLogger adminAuditLogger;

    public Map<Integer, LevelSpecValues> getLevelSpecs(Long id) {
        return combatAdminClient.getBuildingLevelSpecs(id);
    }

    @Transactional
    public Map<Integer, LevelSpecValues> updateLevelSpecs(
            Long adminId, Long id, Map<Integer, LevelSpecValues> specs) {
        Map<Integer, LevelSpecValues> result =
                combatAdminClient.updateBuildingLevelSpecs(id, specs);
        audit(adminId, "BUILDING_LEVEL_SPEC_UPDATE", id, null);
        return result;
    }

    public Map<Integer, Integer> getCastleLimits(Long id) {
        return combatAdminClient.getCastleLimits(id);
    }

    @Transactional
    public Map<Integer, Integer> updateCastleLimits(
            Long adminId, Long id, Map<Integer, Integer> limits) {
        Map<Integer, Integer> result = combatAdminClient.updateCastleLimits(id, limits);
        audit(adminId, "BUILDING_CASTLE_LIMIT_UPDATE", id, null);
        return result;
    }

    public BuildingTypeCatalogResponse getBuildingTypes() {
        return combatAdminClient.getBuildingTypes();
    }

    @Transactional
    public BuildingTypeInfo create(Long adminId, AdminCreateBuildingTypeRequest request) {
        BuildingTypeInfo result = combatAdminClient.createBuildingType(request);
        audit(adminId, "BUILDING_TYPE_CREATE", result.buildingTypeId(), result.name());
        return result;
    }

    @Transactional
    public BuildingTypeInfo update(Long adminId, Long id, AdminUpdateBuildingTypeRequest request) {
        BuildingTypeInfo result = combatAdminClient.updateBuildingType(id, request);
        audit(adminId, "BUILDING_TYPE_UPDATE", id, result.name());
        return result;
    }

    @Transactional
    public void delete(Long adminId, Long id) {
        audit(adminId, "BUILDING_TYPE_DELETE", id, combatAdminClient.deleteBuildingType(id));
    }

    private void audit(Long adminId, String action, Long id, String name) {
        adminAuditLogger.record(
                adminId,
                action,
                "BUILDING_TYPE",
                id,
                name != null ? Map.of("name", name) : Map.of());
    }
}
