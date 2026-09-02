package com.territorial.combat.domain.military.service;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.combat.domain.building.StoragePolicy;
import com.territorial.combat.domain.building.entity.BuildingInstance;
import com.territorial.combat.domain.building.entity.HomeIsland;
import com.territorial.combat.domain.building.repository.BuildingInstanceRepository;
import com.territorial.combat.domain.building.repository.BuildingInstanceRepository.MilitaryLocationSummary;
import com.territorial.combat.domain.building.repository.HomeIslandRepository;
import com.territorial.combat.domain.military.LocationType;
import com.territorial.combat.domain.military.MilitaryPolicy;
import com.territorial.combat.domain.military.config.MilitaryBalanceProperties;
import com.territorial.combat.domain.military.dto.AttackTokenResponse;
import com.territorial.combat.domain.military.dto.DeployUnitRequest;
import com.territorial.combat.domain.military.dto.DeployUnitResponse;
import com.territorial.combat.domain.military.dto.GarrisonUnitResponse;
import com.territorial.combat.domain.military.dto.MoveUnitRequest;
import com.territorial.combat.domain.military.dto.MoveUnitResponse;
import com.territorial.combat.domain.military.dto.ProduceUnitRequest;
import com.territorial.combat.domain.military.dto.ProduceUnitResponse;
import com.territorial.combat.domain.military.dto.RecallUnitRequest;
import com.territorial.combat.domain.military.dto.RecallUnitResponse;
import com.territorial.combat.domain.military.dto.UnitListResponse;
import com.territorial.combat.domain.military.dto.UnitTypeCatalogResponse;
import com.territorial.combat.domain.military.entity.UnitInstance;
import com.territorial.combat.domain.military.entity.UnitResearch;
import com.territorial.combat.domain.military.entity.UnitType;
import com.territorial.combat.domain.military.entity.UnitTypeLevelSpec;
import com.territorial.combat.domain.military.port.MilitaryTerritoryPort;
import com.territorial.combat.domain.military.port.MilitaryTerritoryPort.TerritoryLocation;
import com.territorial.combat.domain.military.repository.AttackTokenRepository;
import com.territorial.combat.domain.military.repository.UnitInstanceRepository;
import com.territorial.combat.domain.military.repository.UnitResearchRepository;
import com.territorial.combat.domain.military.repository.UnitTypeLevelSpecRepository;
import com.territorial.combat.domain.military.repository.UnitTypeRepository;
import com.territorial.combat.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@ConditionalOnBean(MilitaryTerritoryPort.class)
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MilitaryService {

    private final AttackTokenRepository attackTokenRepository;
    private final UnitInstanceRepository unitInstanceRepository;
    private final UnitTypeRepository unitTypeRepository;
    private final HomeIslandRepository homeIslandRepository;
    private final MilitaryTerritoryPort territoryPort;
    private final BuildingInstanceRepository buildingInstanceRepository;
    private final UnitResearchRepository unitResearchRepository;
    private final UnitTypeLevelSpecRepository unitTypeLevelSpecRepository;
    private final MilitaryBalanceProperties balanceProperties;

    public AttackTokenResponse getAttackTokens(Long userId) {
        return attackTokenRepository
                .findByUserId(userId)
                .map(AttackTokenResponse::from)
                .orElse(AttackTokenResponse.empty());
    }

    @Transactional
    public ProduceUnitResponse produceUnit(Long userId, ProduceUnitRequest request) {
        UnitType unitType = findUnitTypeOrThrow(request.unitTypeId());
        int level = levelOf(request.level());
        validateLevelResearched(userId, unitType.getId(), level);
        LocationRef location =
                resolveOwnedLocation(userId, request.locationId(), request.locationType());
        UnitTypeLevelSpec spec = level > 1 ? findLevelSpecOrThrow(unitType.getId(), level) : null;
        validateProductionLocation(
                location,
                spec != null ? spec.getRequiredBarracksLevel() : unitType.getLevel(),
                request.quantity());

        int gpCost = unitType.getCostGp() * request.quantity();
        int foodCost =
                (spec != null ? spec.getTrainCostFood() : unitType.getFoodCost())
                        * request.quantity();
        int gpRemaining =
                chargeLocationGpAndFood(findLocationStoragesWithLock(location), gpCost, foodCost);
        addReadyIdleAtLocation(userId, unitType, level, location, request.quantity());
        log.info(
                "유닛 생산 완료. userId={}, unitTypeId={}, level={}, quantity={}, {}={}",
                userId,
                unitType.getId(),
                level,
                request.quantity(),
                location.type(),
                location.id());
        return ProduceUnitResponse.of(unitType, request.quantity(), gpRemaining);
    }

    @Transactional
    public DeployUnitResponse deployUnit(Long userId, DeployUnitRequest request) {
        findOwnedTerritoryOrThrow(request.territoryId(), userId);
        BuildingInstance building =
                resolveGarrisonBuilding(request.buildingId(), request.territoryId());
        validateGarrisonCapacity(building, request.quantity());
        LocationRef source =
                resolveOwnedLocation(
                        userId, request.sourceLocationId(), request.sourceLocationType());
        int level = levelOf(request.level());
        UnitInstance idle =
                findReadyIdleAtLocationOrThrow(
                        userId, request.unitTypeId(), level, source, request.quantity());
        idle.subtractQuantity(request.quantity());
        addDeployedUnits(
                userId,
                idle.getUnitType(),
                level,
                source,
                request.territoryId(),
                building,
                request.quantity());
        return new DeployUnitResponse(request.quantity(), request.territoryId());
    }

    @Transactional
    public RecallUnitResponse recallUnit(Long userId, RecallUnitRequest request) {
        findOwnedTerritoryOrThrow(request.territoryId(), userId);
        List<UnitInstance> deployed =
                unitInstanceRepository.findDeployedAtTerritory(
                        userId,
                        request.unitTypeId(),
                        levelOf(request.level()),
                        request.territoryId());
        int recalled = recallFromDeployed(deployed, request.quantity());
        int remaining = deployed.stream().mapToInt(UnitInstance::getQuantity).sum();
        return new RecallUnitResponse(recalled, remaining);
    }

    @Transactional
    public MoveUnitResponse moveUnit(Long userId, MoveUnitRequest request) {
        LocationRef source =
                resolveOwnedLocation(
                        userId, request.sourceLocationId(), request.sourceLocationType());
        LocationRef destination =
                resolveOwnedLocation(userId, request.destLocationId(), request.destLocationType());
        validateDifferentLocation(source, destination);
        int level = levelOf(request.level());
        UnitInstance idle =
                findReadyIdleAtLocationOrThrow(
                        userId, request.unitTypeId(), level, source, request.quantity());
        validateUnitCapacityAtLocation(destination, request.quantity());
        int gpRemaining =
                chargeLocationGp(
                        findLocationStoragesWithLock(source),
                        MilitaryPolicy.UNIT_MOVE_COST_GP * request.quantity());
        idle.subtractQuantity(request.quantity());
        LocalDateTime completeAt =
                LocalDateTime.now().plusMinutes(MilitaryPolicy.UNIT_MOVE_MINUTES);
        unitInstanceRepository.save(
                newUnitAtLocation(
                        userId,
                        idle.getUnitType(),
                        level,
                        destination,
                        request.quantity(),
                        completeAt));
        return new MoveUnitResponse(request.quantity(), gpRemaining, completeAt);
    }

    public UnitListResponse getUnitList(Long userId) {
        return buildUnitListResponse(userId, unitInstanceRepository.findByUserId(userId));
    }

    public List<UnitTypeCatalogResponse> getUnitTypeCatalog() {
        return unitTypeRepository.findAll().stream().map(UnitTypeCatalogResponse::from).toList();
    }

    public List<GarrisonUnitResponse> getTerritoryGarrison(Long userId, Long territoryId) {
        Map<UnitType, Integer> byType = new LinkedHashMap<>();
        for (UnitInstance unit :
                unitInstanceRepository.findByUserIdAndDeployedTerritoryId(userId, territoryId)) {
            byType.merge(unit.getUnitType(), unit.getQuantity(), Integer::sum);
        }
        return byType.entrySet().stream()
                .map(
                        entry ->
                                new GarrisonUnitResponse(
                                        entry.getKey().getId(),
                                        entry.getKey().getName(),
                                        entry.getKey().getDisplayName(),
                                        entry.getKey().getIcon(),
                                        entry.getKey().getColorHex(),
                                        entry.getValue()))
                .toList();
    }

    private BuildingInstance resolveGarrisonBuilding(Long buildingId, Long territoryId) {
        BuildingInstance building =
                buildingInstanceRepository
                        .findById(buildingId)
                        .orElseThrow(() -> new CustomException(ErrorCode.BUILDING_NOT_FOUND));
        if (!territoryId.equals(building.getTerritoryId()) || building.isDestroyed()) {
            throw new CustomException(ErrorCode.BUILDING_NOT_FOUND);
        }
        return building;
    }

    private void validateGarrisonCapacity(BuildingInstance building, int quantity) {
        int capacity =
                balanceProperties.garrisonCapacity(building.getBuildingType().getName())
                        * building.getLevel();
        int current =
                nullSafe(unitInstanceRepository.sumQuantityByDeployedBuildingId(building.getId()));
        if (current + quantity > capacity) {
            throw new CustomException(ErrorCode.UNIT_CAPACITY_EXCEEDED);
        }
    }

    private UnitType findUnitTypeOrThrow(Long unitTypeId) {
        return unitTypeRepository
                .findById(unitTypeId)
                .orElseThrow(() -> new CustomException(ErrorCode.UNIT_TYPE_NOT_FOUND));
    }

    private record LocationRef(
            LocationType type, Long id, TerritoryLocation territory, HomeIsland island) {}

    private LocationRef resolveOwnedLocation(Long userId, Long locationId, LocationType type) {
        if (type == LocationType.TERRITORY) {
            return new LocationRef(
                    type, locationId, findOwnedTerritoryOrThrow(locationId, userId), null);
        }
        return new LocationRef(type, locationId, null, findOwnedIslandOrThrow(locationId, userId));
    }

    private TerritoryLocation findOwnedTerritoryOrThrow(Long territoryId, Long userId) {
        TerritoryLocation territory =
                territoryPort
                        .findById(territoryId)
                        .orElseThrow(() -> new CustomException(ErrorCode.TERRITORY_NOT_FOUND));
        if (!userId.equals(territory.ownerId())) {
            throw new CustomException(ErrorCode.NOT_TERRITORY_OWNER);
        }
        return territory;
    }

    private HomeIsland findOwnedIslandOrThrow(Long islandId, Long userId) {
        HomeIsland island =
                homeIslandRepository
                        .findByUserId(userId)
                        .orElseThrow(() -> new CustomException(ErrorCode.ISLAND_NOT_FOUND));
        if (!island.getId().equals(islandId)) {
            throw new CustomException(ErrorCode.ISLAND_NOT_FOUND);
        }
        return island;
    }

    private void validateProductionLocation(LocationRef location, int requiredLevel, int quantity) {
        LocalDateTime now = LocalDateTime.now();
        MilitaryLocationSummary summary =
                location.type() == LocationType.TERRITORY
                        ? buildingInstanceRepository.findMilitaryLocationSummaryByTerritoryId(
                                location.id(), now)
                        : buildingInstanceRepository.findMilitaryLocationSummaryByIslandId(
                                location.id(), now);
        int barracksLevel = nullSafe(summary.getMaxBarracksLevel());
        if (barracksLevel == 0) {
            throw new CustomException(ErrorCode.NO_BARRACKS);
        }
        if (barracksLevel < requiredLevel) {
            throw new CustomException(ErrorCode.BARRACKS_LEVEL_INSUFFICIENT);
        }
        int current = currentQuantity(location);
        int capacity =
                MilitaryPolicy.castleUnitSlots(nullSafe(summary.getCastleLevel()))
                        + nullSafe(summary.getResidenceCapacity());
        if (current + quantity > capacity) {
            throw new CustomException(ErrorCode.UNIT_CAPACITY_EXCEEDED);
        }
    }

    private void validateUnitCapacityAtLocation(LocationRef location, int quantity) {
        if (currentQuantity(location) + quantity > locationCapacity(location)) {
            throw new CustomException(ErrorCode.UNIT_CAPACITY_EXCEEDED);
        }
    }

    private int currentQuantity(LocationRef location) {
        return nullSafe(
                location.type() == LocationType.TERRITORY
                        ? unitInstanceRepository.sumQuantityByHomeTerritoryId(location.id())
                        : unitInstanceRepository.sumQuantityByHomeIslandId(location.id()));
    }

    private int locationCapacity(LocationRef location) {
        int castleLevel =
                (location.type() == LocationType.TERRITORY
                                ? buildingInstanceRepository.findCastleLevelByTerritoryId(
                                        location.id())
                                : buildingInstanceRepository.findCastleLevelByIslandId(
                                        location.id()))
                        .orElse(0);
        int residence =
                nullSafe(
                        location.type() == LocationType.TERRITORY
                                ? buildingInstanceRepository.sumResidenceCapacityByTerritoryId(
                                        location.id(), LocalDateTime.now())
                                : buildingInstanceRepository.sumResidenceCapacityByIslandId(
                                        location.id(), LocalDateTime.now()));
        return MilitaryPolicy.castleUnitSlots(castleLevel) + residence;
    }

    private List<BuildingInstance> findLocationStoragesWithLock(LocationRef location) {
        return location.type() == LocationType.TERRITORY
                ? buildingInstanceRepository.findStorageBuildingsByTerritoryIdWithLock(
                        location.id())
                : buildingInstanceRepository.findStorageBuildingsByIslandIdWithLock(location.id());
    }

    private int chargeLocationGpAndFood(List<BuildingInstance> storages, int gpCost, int foodCost) {
        if (storages.isEmpty()) {
            throw new CustomException(ErrorCode.STORAGE_NOT_FOUND);
        }
        if (StoragePolicy.totalGp(storages) < gpCost) {
            throw new CustomException(ErrorCode.INSUFFICIENT_GP);
        }
        if (StoragePolicy.totalFood(storages) < foodCost) {
            throw new CustomException(ErrorCode.FOOD_INSUFFICIENT);
        }
        StoragePolicy.drainGp(storages, gpCost);
        StoragePolicy.drainFood(storages, foodCost);
        return StoragePolicy.totalGp(storages);
    }

    private int chargeLocationGp(List<BuildingInstance> storages, int gpCost) {
        if (storages.isEmpty()) {
            throw new CustomException(ErrorCode.STORAGE_NOT_FOUND);
        }
        if (StoragePolicy.totalGp(storages) < gpCost) {
            throw new CustomException(ErrorCode.INSUFFICIENT_GP);
        }
        StoragePolicy.drainGp(storages, gpCost);
        return StoragePolicy.totalGp(storages);
    }

    private void validateDifferentLocation(LocationRef source, LocationRef destination) {
        if (source.type() == destination.type() && source.id().equals(destination.id())) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }

    private int nullSafe(Integer value) {
        return value != null ? value : 0;
    }

    private int levelOf(Integer level) {
        return level != null ? level : 1;
    }

    private void validateLevelResearched(Long userId, Long unitTypeId, int level) {
        if (level <= 1) {
            return;
        }
        int researched =
                unitResearchRepository
                        .findByUserIdAndUnitTypeId(userId, unitTypeId)
                        .map(research -> applyCompletion(research))
                        .orElse(1);
        if (level > researched) {
            throw new CustomException(ErrorCode.UNIT_LEVEL_NOT_RESEARCHED);
        }
    }

    private int applyCompletion(UnitResearch research) {
        research.applyCompletionIfDue(LocalDateTime.now());
        return research.getResearchedLevel();
    }

    private UnitTypeLevelSpec findLevelSpecOrThrow(Long unitTypeId, int level) {
        return unitTypeLevelSpecRepository
                .findByUnitType_IdAndLevel(unitTypeId, level)
                .orElseThrow(() -> new CustomException(ErrorCode.RESEARCH_SPEC_NOT_FOUND));
    }

    private UnitInstance newUnitAtLocation(
            Long userId,
            UnitType unitType,
            int level,
            LocationRef home,
            int quantity,
            LocalDateTime moveCompleteAt) {
        UnitInstance.UnitInstanceBuilder builder =
                UnitInstance.builder()
                        .userId(userId)
                        .unitType(unitType)
                        .quantity(quantity)
                        .level(level)
                        .moveCompleteAt(moveCompleteAt);
        if (home.type() == LocationType.TERRITORY) {
            builder.homeTerritoryId(home.id());
        } else {
            builder.homeIsland(home.island());
        }
        return builder.build();
    }

    private void addReadyIdleAtLocation(
            Long userId, UnitType unitType, int level, LocationRef location, int quantity) {
        findReadyIdleAtLocation(userId, unitType.getId(), level, location)
                .ifPresentOrElse(
                        idle -> idle.addQuantity(quantity),
                        () ->
                                unitInstanceRepository.save(
                                        newUnitAtLocation(
                                                userId, unitType, level, location, quantity,
                                                null)));
    }

    private Optional<UnitInstance> findReadyIdleAtLocation(
            Long userId, Long unitTypeId, int level, LocationRef location) {
        return location.type() == LocationType.TERRITORY
                ? unitInstanceRepository.findReadyIdleAtTerritory(
                        userId, unitTypeId, level, location.id())
                : unitInstanceRepository.findReadyIdleAtIsland(
                        userId, unitTypeId, level, location.id());
    }

    private UnitInstance findReadyIdleAtLocationOrThrow(
            Long userId, Long unitTypeId, int level, LocationRef location, int required) {
        UnitInstance idle =
                findReadyIdleAtLocation(userId, unitTypeId, level, location)
                        .orElseThrow(() -> new CustomException(ErrorCode.INSUFFICIENT_UNITS));
        if (idle.getQuantity() < required) {
            throw new CustomException(ErrorCode.INSUFFICIENT_UNITS);
        }
        return idle;
    }

    private void addDeployedUnits(
            Long userId,
            UnitType unitType,
            int level,
            LocationRef source,
            Long territoryId,
            BuildingInstance building,
            int quantity) {
        Optional<UnitInstance> existing =
                source.type() == LocationType.TERRITORY
                        ? unitInstanceRepository.findDeployedFromTerritory(
                                userId, unitType.getId(), level, source.id(), building.getId())
                        : unitInstanceRepository.findDeployedFromIsland(
                                userId, unitType.getId(), level, source.id(), building.getId());
        existing.ifPresentOrElse(
                deployed -> deployed.addQuantity(quantity),
                () -> {
                    UnitInstance deployed =
                            newUnitAtLocation(userId, unitType, level, source, quantity, null);
                    deployed.deployTo(territoryId, building);
                    unitInstanceRepository.save(deployed);
                });
    }

    private int recallFromDeployed(List<UnitInstance> deployedStacks, int quantity) {
        int available = deployedStacks.stream().mapToInt(UnitInstance::getQuantity).sum();
        if (available < quantity) {
            throw new CustomException(ErrorCode.INSUFFICIENT_UNITS);
        }
        int remaining = quantity;
        for (UnitInstance deployed : deployedStacks) {
            if (remaining == 0) {
                break;
            }
            int take = Math.min(remaining, deployed.getQuantity());
            deployed.subtractQuantity(take);
            returnToHomeIdle(deployed, take);
            remaining -= take;
        }
        return quantity;
    }

    private void returnToHomeIdle(UnitInstance deployed, int quantity) {
        Long userId = deployed.getUserId();
        UnitType unitType = deployed.getUnitType();
        int level = deployed.getLevel();
        if (deployed.getHomeTerritoryId() != null) {
            Long homeTerritoryId = deployed.getHomeTerritoryId();
            unitInstanceRepository
                    .findReadyIdleAtTerritory(userId, unitType.getId(), level, homeTerritoryId)
                    .ifPresentOrElse(
                            idle -> idle.addQuantity(quantity),
                            () ->
                                    unitInstanceRepository.save(
                                            UnitInstance.builder()
                                                    .userId(userId)
                                                    .unitType(unitType)
                                                    .quantity(quantity)
                                                    .level(level)
                                                    .homeTerritoryId(homeTerritoryId)
                                                    .build()));
        } else {
            HomeIsland homeIsland = deployed.getHomeIsland();
            unitInstanceRepository
                    .findReadyIdleAtIsland(userId, unitType.getId(), level, homeIsland.getId())
                    .ifPresentOrElse(
                            idle -> idle.addQuantity(quantity),
                            () ->
                                    unitInstanceRepository.save(
                                            UnitInstance.builder()
                                                    .userId(userId)
                                                    .unitType(unitType)
                                                    .quantity(quantity)
                                                    .level(level)
                                                    .homeIsland(homeIsland)
                                                    .build()));
        }
    }

    private UnitListResponse buildUnitListResponse(Long userId, List<UnitInstance> instances) {
        Map<String, List<UnitInstance>> byLocation = new LinkedHashMap<>();
        for (UnitInstance instance : instances) {
            byLocation
                    .computeIfAbsent(locationKey(instance), ignored -> new ArrayList<>())
                    .add(instance);
        }
        List<UnitListResponse.LocationUnits> locations = new ArrayList<>();
        for (TerritoryLocation territory : territoryPort.findOwnedByUserId(userId)) {
            LocationRef location =
                    new LocationRef(
                            LocationType.TERRITORY, territory.territoryId(), territory, null);
            locations.add(
                    toLocationUnits(
                            location,
                            territory.coordX(),
                            territory.coordY(),
                            byLocation.getOrDefault("T" + territory.territoryId(), List.of())));
        }
        homeIslandRepository
                .findByUserId(userId)
                .ifPresent(
                        island -> {
                            LocationRef location =
                                    new LocationRef(
                                            LocationType.ISLAND, island.getId(), null, island);
                            locations.add(
                                    toLocationUnits(
                                            location,
                                            null,
                                            null,
                                            byLocation.getOrDefault(
                                                    "I" + island.getId(), List.of())));
                        });
        return new UnitListResponse(locations);
    }

    private String locationKey(UnitInstance instance) {
        return instance.getHomeTerritoryId() != null
                ? "T" + instance.getHomeTerritoryId()
                : "I" + instance.getHomeIsland().getId();
    }

    private UnitListResponse.LocationUnits toLocationUnits(
            LocationRef location, Integer coordX, Integer coordY, List<UnitInstance> units) {
        List<BuildingInstance> storages =
                location.type() == LocationType.TERRITORY
                        ? buildingInstanceRepository.findStorageBuildingsByTerritoryId(
                                location.id())
                        : buildingInstanceRepository.findStorageBuildingsByIslandId(location.id());
        Map<Long, List<UnitInstance>> byType = new LinkedHashMap<>();
        for (UnitInstance unit : units) {
            byType.computeIfAbsent(unit.getUnitType().getId(), ignored -> new ArrayList<>())
                    .add(unit);
        }
        List<UnitListResponse.UnitDto> unitDtos =
                byType.values().stream().map(this::toUnitDto).toList();
        return new UnitListResponse.LocationUnits(
                location.type().name(),
                location.id(),
                coordX,
                coordY,
                locationCapacity(location),
                StoragePolicy.totalFood(storages),
                unitDtos);
    }

    private UnitListResponse.UnitDto toUnitDto(List<UnitInstance> group) {
        UnitType type = group.get(0).getUnitType();
        int total = group.stream().mapToInt(UnitInstance::getQuantity).sum();
        int deployed =
                group.stream()
                        .filter(unit -> unit.getDeployedTerritoryId() != null)
                        .mapToInt(UnitInstance::getQuantity)
                        .sum();
        int inTransit =
                group.stream()
                        .filter(UnitInstance::isInTransit)
                        .mapToInt(UnitInstance::getQuantity)
                        .sum();
        return new UnitListResponse.UnitDto(
                type.getId(),
                type.getName(),
                type.getDisplayName(),
                type.getIcon(),
                type.getColorHex(),
                total,
                deployed,
                total - deployed - inTransit,
                inTransit,
                type.getAttackPower(),
                type.getDefensePower(),
                type.getCostGp(),
                type.getFoodCost(),
                type.getBuildingDamage(),
                type.getLevel());
    }
}
