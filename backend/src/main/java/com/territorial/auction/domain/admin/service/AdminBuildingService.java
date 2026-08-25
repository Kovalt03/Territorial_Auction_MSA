package com.territorial.auction.domain.admin.service;

import com.territorial.auction.domain.admin.dto.AdminCreateBuildingTypeRequest;
import com.territorial.auction.domain.admin.dto.AdminLevelSpecsRequest.LevelSpecValues;
import com.territorial.auction.domain.admin.dto.AdminUpdateBuildingTypeRequest;
import com.territorial.auction.domain.building.BuildingPolicy;
import com.territorial.auction.domain.building.dto.BuildingTypeCatalogResponse;
import com.territorial.auction.domain.building.dto.BuildingTypeCatalogResponse.BuildingTypeInfo;
import com.territorial.auction.domain.building.entity.BuildingCategory;
import com.territorial.auction.domain.building.entity.BuildingLevelSpec;
import com.territorial.auction.domain.building.entity.BuildingType;
import com.territorial.auction.domain.building.repository.BuildingInstanceRepository;
import com.territorial.auction.domain.building.repository.BuildingLevelSpecRepository;
import com.territorial.auction.domain.building.repository.BuildingTypeRepository;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminBuildingService {

    private final BuildingTypeRepository buildingTypeRepository;
    private final BuildingInstanceRepository buildingInstanceRepository;
    private final BuildingLevelSpecRepository buildingLevelSpecRepository;
    private final com.territorial.auction.domain.building.repository.BuildingCastleLimitRepository
            buildingCastleLimitRepository;
    private final AdminAuditLogger adminAuditLogger;

    // 건물별 레벨 스펙 조회: {도달레벨: 값들}. 비어있지 않은 것만 담는다.
    public Map<Integer, LevelSpecValues> getLevelSpecs(Long buildingTypeId) {
        findOrThrow(buildingTypeId);
        Map<Integer, LevelSpecValues> specs = new LinkedHashMap<>();
        buildingLevelSpecRepository.findAllByBuildingType_Id(buildingTypeId).stream()
                .filter(s -> !s.isEmpty())
                .sorted((a, b) -> a.getLevel() - b.getLevel())
                .forEach(s -> specs.put(s.getLevel(), LevelSpecValues.from(s)));
        return specs;
    }

    // {도달레벨: 값들} 설정. 각 값이 null이면 해당 항목은 공식 폴백. 모두 null이면 스펙 삭제.
    @Transactional
    public Map<Integer, LevelSpecValues> updateLevelSpecs(
            Long adminUserId, Long buildingTypeId, Map<Integer, LevelSpecValues> specs) {
        BuildingType type = findOrThrow(buildingTypeId);
        specs.forEach((level, values) -> applyLevelSpec(type, level, values));

        adminAuditLogger.record(
                adminUserId,
                "BUILDING_LEVEL_SPEC_UPDATE",
                "BUILDING_TYPE",
                buildingTypeId,
                Map.of("name", type.getName()));
        return getLevelSpecs(buildingTypeId);
    }

    private void applyLevelSpec(BuildingType type, Integer level, LevelSpecValues v) {
        if (level == null || level < 2 || level > BuildingPolicy.MAX_LEVEL) {
            throw new CustomException(ErrorCode.INVALID_BUILDING_LEVEL);
        }
        // 장식 건물은 생산 기능이 없으므로 레벨 스펙에서도 식량/유닛/GP를 무효화한다.
        boolean isDecorative = type.getCategory() == BuildingCategory.DECORATIVE;
        Integer food = isDecorative ? null : v.foodProductionRate();
        Integer unit = isDecorative ? null : v.unitCapacityPerLevel();
        Integer gp = isDecorative ? null : v.gpProductionRate();
        buildingLevelSpecRepository
                .findByBuildingType_IdAndLevel(type.getId(), level)
                .ifPresentOrElse(
                        spec -> {
                            spec.update(
                                    v.upgradeCostGp(),
                                    v.maxHp(),
                                    v.defensePower(),
                                    food,
                                    unit,
                                    gp,
                                    v.upgradeTimeSeconds());
                            if (spec.isEmpty()) buildingLevelSpecRepository.delete(spec);
                        },
                        () -> {
                            BuildingLevelSpec created =
                                    BuildingLevelSpec.builder()
                                            .buildingType(type)
                                            .level(level)
                                            .upgradeCostGp(v.upgradeCostGp())
                                            .maxHp(v.maxHp())
                                            .defensePower(v.defensePower())
                                            .foodProductionRate(food)
                                            .unitCapacityPerLevel(unit)
                                            .gpProductionRate(gp)
                                            .upgradeTimeSeconds(v.upgradeTimeSeconds())
                                            .build();
                            if (!created.isEmpty()) buildingLevelSpecRepository.save(created);
                        });
    }

    // 성 레벨별 건물 개수 상한 조회: {성 레벨: 최대 개수}
    public Map<Integer, Integer> getCastleLimits(Long buildingTypeId) {
        findOrThrow(buildingTypeId);
        Map<Integer, Integer> limits = new LinkedHashMap<>();
        buildingCastleLimitRepository.findAllByBuildingType_Id(buildingTypeId).stream()
                .sorted((a, b) -> a.getCastleLevel() - b.getCastleLevel())
                .forEach(l -> limits.put(l.getCastleLevel(), l.getMaxCount()));
        return limits;
    }

    // 값이 null이면 해당 성 레벨의 상한을 제거한다(제한 없음).
    @Transactional
    public Map<Integer, Integer> updateCastleLimits(
            Long adminUserId, Long buildingTypeId, Map<Integer, Integer> limits) {
        BuildingType type = findOrThrow(buildingTypeId);
        if (type.isCastle()) {
            throw new CustomException(ErrorCode.CASTLE_LIMIT_NOT_CONFIGURABLE);
        }
        limits.forEach((castleLevel, maxCount) -> applyCastleLimit(type, castleLevel, maxCount));

        adminAuditLogger.record(
                adminUserId,
                "BUILDING_CASTLE_LIMIT_UPDATE",
                "BUILDING_TYPE",
                buildingTypeId,
                Map.of("name", type.getName()));
        return getCastleLimits(buildingTypeId);
    }

    private void applyCastleLimit(BuildingType type, Integer castleLevel, Integer maxCount) {
        if (castleLevel == null || castleLevel < 1 || castleLevel > BuildingPolicy.MAX_LEVEL) {
            throw new CustomException(ErrorCode.INVALID_BUILDING_LEVEL);
        }
        buildingCastleLimitRepository
                .findByBuildingType_IdAndCastleLevel(type.getId(), castleLevel)
                .ifPresentOrElse(
                        limit -> {
                            if (maxCount == null) buildingCastleLimitRepository.delete(limit);
                            else limit.updateMaxCount(maxCount);
                        },
                        () -> {
                            if (maxCount == null) return;
                            buildingCastleLimitRepository.save(
                                    com.territorial.auction.domain.building.entity
                                            .BuildingCastleLimit.builder()
                                            .buildingType(type)
                                            .castleLevel(castleLevel)
                                            .maxCount(maxCount)
                                            .build());
                        });
    }

    public BuildingTypeCatalogResponse getBuildingTypes() {
        return BuildingTypeCatalogResponse.of(buildingTypeRepository.findAll());
    }

    @Transactional
    public BuildingTypeInfo create(Long adminUserId, AdminCreateBuildingTypeRequest request) {
        String name = request.name().trim().toUpperCase();
        if (buildingTypeRepository.existsByName(name)) {
            throw new CustomException(ErrorCode.DUPLICATE_BUILDING_TYPE_NAME);
        }
        // 기능은 백엔드가 코드로 하드코딩 매칭하므로, 신규 생성은 장식 건물만 허용한다.
        if (BuildingCategory.FUNCTIONAL_CODES.contains(name)) {
            throw new CustomException(ErrorCode.FUNCTIONAL_BUILDING_NOT_CREATABLE);
        }
        BuildingType saved =
                buildingTypeRepository.save(
                        BuildingType.builder()
                                .name(name)
                                .displayName(blankToNull(request.displayName()))
                                .category(BuildingCategory.DECORATIVE)
                                .width(request.width())
                                .height(request.height())
                                .maxHp(request.maxHp())
                                .baseCostGp(request.baseCostGp())
                                .upgradeCostGp(request.upgradeCostGp())
                                .apCost(request.apCost())
                                .zoneRestriction(request.zoneRestriction())
                                .defensePower(request.defensePower())
                                // 장식 건물은 생산 기능이 없다(이름 기반 로직이 없음).
                                .foodProductionRate(null)
                                .unitCapacityPerLevel(null)
                                .gpProductionRate(null)
                                .buildTimeSeconds(request.buildTimeSeconds())
                                .upgradeTimeSeconds(request.upgradeTimeSeconds())
                                .icon(blankToNull(request.icon()))
                                .colorHex(blankToNull(request.colorHex()))
                                .build());

        adminAuditLogger.record(
                adminUserId,
                "BUILDING_TYPE_CREATE",
                "BUILDING_TYPE",
                saved.getId(),
                Map.of("name", name));
        return BuildingTypeInfo.from(saved);
    }

    @Transactional
    public BuildingTypeInfo update(
            Long adminUserId, Long buildingTypeId, AdminUpdateBuildingTypeRequest request) {
        BuildingType type = findOrThrow(buildingTypeId);
        boolean isDecorative = type.getCategory() == BuildingCategory.DECORATIVE;
        type.update(
                blankToNull(request.displayName()),
                request.width(),
                request.height(),
                request.maxHp(),
                request.baseCostGp(),
                request.upgradeCostGp(),
                // AP 판매가는 장식 건물만 유효.
                isDecorative ? request.apCost() : null,
                request.zoneRestriction(),
                request.defensePower(),
                // 장식 건물은 생산 필드를 강제로 비운다(기능이 없으므로).
                isDecorative ? null : request.foodProductionRate(),
                isDecorative ? null : request.unitCapacityPerLevel(),
                isDecorative ? null : request.gpProductionRate(),
                request.buildTimeSeconds(),
                request.upgradeTimeSeconds(),
                blankToNull(request.icon()),
                blankToNull(request.colorHex()));

        adminAuditLogger.record(
                adminUserId,
                "BUILDING_TYPE_UPDATE",
                "BUILDING_TYPE",
                buildingTypeId,
                Map.of("name", type.getName()));
        return BuildingTypeInfo.from(type);
    }

    @Transactional
    public void delete(Long adminUserId, Long buildingTypeId) {
        BuildingType type = findOrThrow(buildingTypeId);
        if (buildingInstanceRepository.countByBuildingType_Id(buildingTypeId) > 0) {
            throw new CustomException(ErrorCode.BUILDING_TYPE_IN_USE);
        }
        buildingTypeRepository.delete(type);
        adminAuditLogger.record(
                adminUserId,
                "BUILDING_TYPE_DELETE",
                "BUILDING_TYPE",
                buildingTypeId,
                Map.of("name", type.getName()));
    }

    private BuildingType findOrThrow(Long buildingTypeId) {
        return buildingTypeRepository
                .findById(buildingTypeId)
                .orElseThrow(() -> new CustomException(ErrorCode.BUILDING_TYPE_NOT_FOUND));
    }

    private String blankToNull(String v) {
        return (v != null && !v.isBlank()) ? v.trim() : null;
    }
}
