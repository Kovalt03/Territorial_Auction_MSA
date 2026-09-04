package com.territorial.admin.client;

import com.territorial.admin.domain.admin.dto.AdminBuildingTypeCatalogResponse;
import com.territorial.admin.domain.admin.dto.AdminBuildingTypeCatalogResponse.BuildingTypeInfo;
import com.territorial.admin.domain.admin.dto.AdminCreateBuildingTypeRequest;
import com.territorial.admin.domain.admin.dto.AdminLevelSpecsRequest.LevelSpecValues;
import com.territorial.admin.domain.admin.dto.AdminUnitLevelSpecsRequest.UnitLevelValues;
import com.territorial.admin.domain.admin.dto.AdminUnitTypeResponse;
import com.territorial.admin.domain.admin.dto.AdminUpdateBuildingTypeRequest;
import com.territorial.admin.domain.admin.dto.AdminUpdateUnitTypeRequest;
import java.util.List;
import java.util.Map;

/** combat-service가 소유한 건물·유닛·자원 관리 계약. 인증과 감사 로그는 호출 측 admin에 남는다. */
public interface CombatAdminClient {
    AdminBuildingTypeCatalogResponse getBuildingTypes();

    BuildingTypeInfo createBuildingType(AdminCreateBuildingTypeRequest request);

    BuildingTypeInfo updateBuildingType(Long id, AdminUpdateBuildingTypeRequest request);

    String deleteBuildingType(Long id);

    Map<Integer, LevelSpecValues> getBuildingLevelSpecs(Long id);

    Map<Integer, LevelSpecValues> updateBuildingLevelSpecs(
            Long id, Map<Integer, LevelSpecValues> specs);

    Map<Integer, Integer> getCastleLimits(Long id);

    Map<Integer, Integer> updateCastleLimits(Long id, Map<Integer, Integer> limits);

    List<AdminUnitTypeResponse> getUnitTypes();

    AdminUnitTypeResponse updateUnitType(Long id, AdminUpdateUnitTypeRequest request);

    Map<Integer, UnitLevelValues> getUnitLevelSpecs(Long id);

    Map<Integer, UnitLevelValues> updateUnitLevelSpecs(
            Long id, Map<Integer, UnitLevelValues> specs);

    long getTotalStoredGp();

    UserResourceSnapshot getUserResources(Long userId, List<Long> territoryIds);

    int adjustGp(Long userId, int delta, String commandKey);

    record UserResourceSnapshot(int availableGp, int availableFood) {}
}
