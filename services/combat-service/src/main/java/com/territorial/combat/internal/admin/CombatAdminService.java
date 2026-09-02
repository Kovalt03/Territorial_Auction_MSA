package com.territorial.combat.internal.admin;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.combat.domain.building.BuildingPolicy;
import com.territorial.combat.domain.building.entity.BuildingCastleLimit;
import com.territorial.combat.domain.building.entity.BuildingCategory;
import com.territorial.combat.domain.building.entity.BuildingLevelSpec;
import com.territorial.combat.domain.building.entity.BuildingType;
import com.territorial.combat.domain.building.entity.GlobalVault;
import com.territorial.combat.domain.building.repository.BuildingCastleLimitRepository;
import com.territorial.combat.domain.building.repository.BuildingInstanceRepository;
import com.territorial.combat.domain.building.repository.BuildingLevelSpecRepository;
import com.territorial.combat.domain.building.repository.BuildingTypeRepository;
import com.territorial.combat.domain.building.repository.CombatUserSnapshotRepository;
import com.territorial.combat.domain.building.repository.GlobalVaultRepository;
import com.territorial.combat.domain.military.UnitPolicy;
import com.territorial.combat.domain.military.entity.UnitType;
import com.territorial.combat.domain.military.entity.UnitTypeLevelSpec;
import com.territorial.combat.domain.military.repository.UnitTypeLevelSpecRepository;
import com.territorial.combat.domain.military.repository.UnitTypeRepository;
import com.territorial.combat.global.exception.ErrorCode;
import com.territorial.combat.internal.admin.CombatAdminContract.AdjustGpRequest;
import com.territorial.combat.internal.admin.CombatAdminContract.BuildingTypeCatalog;
import com.territorial.combat.internal.admin.CombatAdminContract.BuildingTypeView;
import com.territorial.combat.internal.admin.CombatAdminContract.CreateBuildingTypeRequest;
import com.territorial.combat.internal.admin.CombatAdminContract.GpBalanceView;
import com.territorial.combat.internal.admin.CombatAdminContract.LevelSpecValues;
import com.territorial.combat.internal.admin.CombatAdminContract.UnitLevelValues;
import com.territorial.combat.internal.admin.CombatAdminContract.UnitTypeView;
import com.territorial.combat.internal.admin.CombatAdminContract.UpdateBuildingTypeRequest;
import com.territorial.combat.internal.admin.CombatAdminContract.UpdateUnitTypeRequest;
import com.territorial.combat.internal.admin.CombatAdminContract.UserResourceView;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CombatAdminService {

    private static final String ADJUST_GP = "ADMIN_ADJUST_GP";

    private final BuildingTypeRepository buildingTypeRepository;
    private final BuildingInstanceRepository buildingInstanceRepository;
    private final BuildingLevelSpecRepository buildingLevelSpecRepository;
    private final BuildingCastleLimitRepository buildingCastleLimitRepository;
    private final UnitTypeRepository unitTypeRepository;
    private final UnitTypeLevelSpecRepository unitTypeLevelSpecRepository;
    private final GlobalVaultRepository globalVaultRepository;
    private final CombatUserSnapshotRepository userSnapshotRepository;
    private final CombatCommandRepository commandRepository;

    public BuildingTypeCatalog getBuildingTypes() {
        return new BuildingTypeCatalog(
                buildingTypeRepository.findAll().stream().map(BuildingTypeView::from).toList());
    }

    @Transactional
    public BuildingTypeView createBuildingType(CreateBuildingTypeRequest request) {
        String name = request.name().trim().toUpperCase();
        if (buildingTypeRepository.existsByName(name)) {
            throw new CustomException(ErrorCode.DUPLICATE_BUILDING_TYPE_NAME);
        }
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
                                .buildTimeSeconds(request.buildTimeSeconds())
                                .upgradeTimeSeconds(request.upgradeTimeSeconds())
                                .icon(blankToNull(request.icon()))
                                .colorHex(blankToNull(request.colorHex()))
                                .build());
        return BuildingTypeView.from(saved);
    }

    @Transactional
    public BuildingTypeView updateBuildingType(Long id, UpdateBuildingTypeRequest request) {
        BuildingType type = findBuildingType(id);
        boolean decorative = type.getCategory() == BuildingCategory.DECORATIVE;
        type.update(
                blankToNull(request.displayName()),
                request.width(),
                request.height(),
                request.maxHp(),
                request.baseCostGp(),
                request.upgradeCostGp(),
                decorative ? request.apCost() : null,
                request.zoneRestriction(),
                request.defensePower(),
                decorative ? null : request.foodProductionRate(),
                decorative ? null : request.unitCapacityPerLevel(),
                decorative ? null : request.gpProductionRate(),
                request.buildTimeSeconds(),
                request.upgradeTimeSeconds(),
                blankToNull(request.icon()),
                blankToNull(request.colorHex()));
        return BuildingTypeView.from(type);
    }

    @Transactional
    public String deleteBuildingType(Long id) {
        BuildingType type = findBuildingType(id);
        if (buildingInstanceRepository.countByBuildingType_Id(id) > 0) {
            throw new CustomException(ErrorCode.BUILDING_TYPE_IN_USE);
        }
        buildingTypeRepository.delete(type);
        return type.getName();
    }

    public Map<Integer, LevelSpecValues> getBuildingLevelSpecs(Long id) {
        findBuildingType(id);
        Map<Integer, LevelSpecValues> result = new LinkedHashMap<>();
        buildingLevelSpecRepository.findAllByBuildingType_Id(id).stream()
                .filter(spec -> !spec.isEmpty())
                .sorted((a, b) -> Integer.compare(a.getLevel(), b.getLevel()))
                .forEach(spec -> result.put(spec.getLevel(), LevelSpecValues.from(spec)));
        return result;
    }

    @Transactional
    public Map<Integer, LevelSpecValues> updateBuildingLevelSpecs(
            Long id, Map<Integer, LevelSpecValues> specs) {
        BuildingType type = findBuildingType(id);
        specs.forEach((level, values) -> applyBuildingLevelSpec(type, level, values));
        return getBuildingLevelSpecs(id);
    }

    public Map<Integer, Integer> getCastleLimits(Long id) {
        findBuildingType(id);
        Map<Integer, Integer> result = new LinkedHashMap<>();
        buildingCastleLimitRepository.findAllByBuildingType_Id(id).stream()
                .sorted((a, b) -> Integer.compare(a.getCastleLevel(), b.getCastleLevel()))
                .forEach(limit -> result.put(limit.getCastleLevel(), limit.getMaxCount()));
        return result;
    }

    @Transactional
    public Map<Integer, Integer> updateCastleLimits(Long id, Map<Integer, Integer> limits) {
        BuildingType type = findBuildingType(id);
        if (type.isCastle()) {
            throw new CustomException(ErrorCode.CASTLE_LIMIT_NOT_CONFIGURABLE);
        }
        limits.forEach((level, maxCount) -> applyCastleLimit(type, level, maxCount));
        return getCastleLimits(id);
    }

    public List<UnitTypeView> getUnitTypes() {
        return unitTypeRepository.findAll().stream().map(UnitTypeView::from).toList();
    }

    @Transactional
    public UnitTypeView updateUnitType(Long id, UpdateUnitTypeRequest request) {
        UnitType type = findUnitType(id);
        type.update(
                blankToNull(request.displayName()),
                blankToNull(request.icon()),
                blankToNull(request.colorHex()),
                request.attackPower(),
                request.defensePower(),
                request.costGp(),
                request.foodCost(),
                request.buildingDamage(),
                request.level());
        return UnitTypeView.from(type);
    }

    public Map<Integer, UnitLevelValues> getUnitLevelSpecs(Long id) {
        findUnitType(id);
        Map<Integer, UnitLevelValues> result = new LinkedHashMap<>();
        unitTypeLevelSpecRepository.findAllByUnitType_Id(id).stream()
                .sorted((a, b) -> Integer.compare(a.getLevel(), b.getLevel()))
                .forEach(spec -> result.put(spec.getLevel(), UnitLevelValues.from(spec)));
        return result;
    }

    @Transactional
    public Map<Integer, UnitLevelValues> updateUnitLevelSpecs(
            Long id, Map<Integer, UnitLevelValues> specs) {
        UnitType type = findUnitType(id);
        specs.forEach((level, values) -> applyUnitLevelSpec(type, level, values));
        return getUnitLevelSpecs(id);
    }

    public long getTotalStoredGp() {
        return globalVaultRepository.sumStoredGp() + buildingInstanceRepository.sumAllStoredGp();
    }

    public UserResourceView getUserResources(Long userId, List<Long> territoryIds) {
        int gp = globalVaultRepository.findById(userId).map(GlobalVault::getStoredGp).orElse(0);
        int food = buildingInstanceRepository.sumStoredFoodByOwnerId(userId, territoryIds);
        return new UserResourceView(gp, food);
    }

    @Transactional
    public GpBalanceView adjustGp(AdjustGpRequest request) {
        String fingerprint = request.userId() + ":" + request.delta();
        commandRepository
                .findById(request.commandKey())
                .ifPresent(
                        command -> {
                            if (!ADJUST_GP.equals(command.getCommandType())
                                    || !fingerprint.equals(command.getRequestFingerprint())) {
                                throw new CustomException(ErrorCode.WALLET_COMMAND_CONFLICT);
                            }
                        });
        if (!commandRepository.existsById(request.commandKey())) {
            GlobalVault vault =
                    globalVaultRepository
                            .findByIdWithLock(request.userId())
                            .orElseGet(() -> createVault(request.userId()));
            vault.receiveGp(request.delta());
            commandRepository.save(new CombatCommand(request.commandKey(), ADJUST_GP, fingerprint));
        }
        int availableGp =
                globalVaultRepository
                        .findById(request.userId())
                        .map(GlobalVault::getStoredGp)
                        .orElse(0);
        return new GpBalanceView(availableGp);
    }

    private void applyBuildingLevelSpec(BuildingType type, Integer level, LevelSpecValues values) {
        if (level == null || level < 2 || level > BuildingPolicy.MAX_LEVEL) {
            throw new CustomException(ErrorCode.INVALID_BUILDING_LEVEL);
        }
        boolean decorative = type.getCategory() == BuildingCategory.DECORATIVE;
        Integer food = decorative ? null : values.foodProductionRate();
        Integer unit = decorative ? null : values.unitCapacityPerLevel();
        Integer gp = decorative ? null : values.gpProductionRate();
        buildingLevelSpecRepository
                .findByBuildingType_IdAndLevel(type.getId(), level)
                .ifPresentOrElse(
                        spec -> {
                            spec.update(
                                    values.upgradeCostGp(),
                                    values.maxHp(),
                                    values.defensePower(),
                                    food,
                                    unit,
                                    gp,
                                    values.upgradeTimeSeconds());
                            if (spec.isEmpty()) buildingLevelSpecRepository.delete(spec);
                        },
                        () -> {
                            BuildingLevelSpec created =
                                    BuildingLevelSpec.builder()
                                            .buildingType(type)
                                            .level(level)
                                            .upgradeCostGp(values.upgradeCostGp())
                                            .maxHp(values.maxHp())
                                            .defensePower(values.defensePower())
                                            .foodProductionRate(food)
                                            .unitCapacityPerLevel(unit)
                                            .gpProductionRate(gp)
                                            .upgradeTimeSeconds(values.upgradeTimeSeconds())
                                            .build();
                            if (!created.isEmpty()) buildingLevelSpecRepository.save(created);
                        });
    }

    private void applyCastleLimit(BuildingType type, Integer level, Integer maxCount) {
        if (level == null || level < 1 || level > BuildingPolicy.MAX_LEVEL) {
            throw new CustomException(ErrorCode.INVALID_BUILDING_LEVEL);
        }
        buildingCastleLimitRepository
                .findByBuildingType_IdAndCastleLevel(type.getId(), level)
                .ifPresentOrElse(
                        limit -> {
                            if (maxCount == null) buildingCastleLimitRepository.delete(limit);
                            else limit.updateMaxCount(maxCount);
                        },
                        () -> {
                            if (maxCount != null) {
                                buildingCastleLimitRepository.save(
                                        BuildingCastleLimit.builder()
                                                .buildingType(type)
                                                .castleLevel(level)
                                                .maxCount(maxCount)
                                                .build());
                            }
                        });
    }

    private void applyUnitLevelSpec(UnitType type, Integer level, UnitLevelValues values) {
        if (level == null || level < 2 || level > UnitPolicy.MAX_LEVEL) {
            throw new CustomException(ErrorCode.INVALID_UNIT_LEVEL);
        }
        unitTypeLevelSpecRepository
                .findByUnitType_IdAndLevel(type.getId(), level)
                .ifPresentOrElse(
                        spec -> {
                            if (values.hasNoValues()) {
                                unitTypeLevelSpecRepository.delete(spec);
                            } else {
                                validateUnitLevelValues(values);
                                spec.update(
                                        values.attackPower(),
                                        values.defensePower(),
                                        values.trainCostFood(),
                                        values.requiredBarracksLevel());
                            }
                        },
                        () -> {
                            if (!values.hasNoValues()) {
                                validateUnitLevelValues(values);
                                unitTypeLevelSpecRepository.save(
                                        UnitTypeLevelSpec.builder()
                                                .unitType(type)
                                                .level(level)
                                                .attackPower(values.attackPower())
                                                .defensePower(values.defensePower())
                                                .trainCostFood(values.trainCostFood())
                                                .requiredBarracksLevel(
                                                        values.requiredBarracksLevel())
                                                .build());
                            }
                        });
    }

    private void validateUnitLevelValues(UnitLevelValues values) {
        if (values.attackPower() == null
                || values.defensePower() == null
                || values.trainCostFood() == null
                || values.requiredBarracksLevel() == null) {
            throw new CustomException(ErrorCode.INCOMPLETE_UNIT_LEVEL_SPEC);
        }
    }

    private BuildingType findBuildingType(Long id) {
        return buildingTypeRepository
                .findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.BUILDING_TYPE_NOT_FOUND));
    }

    private UnitType findUnitType(Long id) {
        return unitTypeRepository
                .findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.UNIT_TYPE_NOT_FOUND));
    }

    private GlobalVault createVault(Long userId) {
        if (!userSnapshotRepository.existsById(userId)) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }
        return globalVaultRepository.save(GlobalVault.builder().userId(userId).build());
    }

    private String blankToNull(String value) {
        return value != null && !value.isBlank() ? value.trim() : null;
    }
}
