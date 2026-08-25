package com.territorial.auction.domain.military.service;

import com.territorial.auction.domain.building.BuildingLevelSpecResolver;
import com.territorial.auction.domain.building.StoragePolicy;
import com.territorial.auction.domain.building.entity.BuildingInstance;
import com.territorial.auction.domain.building.entity.GlobalVault;
import com.territorial.auction.domain.building.entity.HomeIsland;
import com.territorial.auction.domain.building.repository.BuildingInstanceRepository;
import com.territorial.auction.domain.building.repository.BuildingInstanceRepository.MilitaryLocationSummary;
import com.territorial.auction.domain.building.repository.BuildingLevelSpecRepository;
import com.territorial.auction.domain.building.repository.GlobalVaultRepository;
import com.territorial.auction.domain.building.repository.HomeIslandRepository;
import com.territorial.auction.domain.map.entity.Territory;
import com.territorial.auction.domain.map.repository.TerritoryRepository;
import com.territorial.auction.domain.military.LocationType;
import com.territorial.auction.domain.military.MilitaryPolicy;
import com.territorial.auction.domain.military.dto.*;
import com.territorial.auction.domain.military.entity.*;
import com.territorial.auction.domain.military.event.GarrisonBuildingDestroyedEvent;
import com.territorial.auction.domain.military.event.TerritoryLostEvent;
import com.territorial.auction.domain.military.repository.*;
import com.territorial.auction.domain.notification.entity.NotificationLog;
import com.territorial.auction.domain.notification.service.NotificationService;
import com.territorial.auction.domain.user.entity.User;
import com.territorial.auction.domain.user.repository.UserRepository;
import com.territorial.auction.global.config.BalanceConfig;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MilitaryService {

    private static final String SCOUT_UNIT_NAME = "SCOUT";

    private final AttackTokenRepository attackTokenRepository;
    private final UnitInstanceRepository unitInstanceRepository;
    private final UnitTypeRepository unitTypeRepository;
    private final HomeIslandRepository homeIslandRepository;
    private final SiegeEventRepository siegeEventRepository;
    private final SiegeForceRepository siegeForceRepository;
    private final SiegeResultRepository siegeResultRepository;
    private final UserRepository userRepository;
    private final TerritoryRepository territoryRepository;
    private final BuildingInstanceRepository buildingInstanceRepository;
    private final BuildingLevelSpecRepository buildingLevelSpecRepository;
    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;
    private final BalanceConfig balanceConfig;
    private final SiegeStructureRepository siegeStructureRepository;
    private final GlobalVaultRepository globalVaultRepository;
    private final UnitResearchRepository unitResearchRepository;
    private final UnitTypeLevelSpecRepository unitTypeLevelSpecRepository;

    public AttackTokenResponse getAttackTokens(Long userId) {
        return attackTokenRepository
                .findByUserId(userId)
                .map(AttackTokenResponse::from)
                .orElse(AttackTokenResponse.empty());
    }

    /** 공성 대상 정찰 — 대상 영토의 존별 실제 HP와 정밀 공격 대상 건물 목록을 반환한다. 방어 병력 구성은 정보 비대칭(공성 설계 §7-①)상 포함하지 않는다. */
    public SiegeTargetResponse getSiegeTarget(Long territoryId) {
        Territory target = findTerritoryOrThrow(territoryId);
        validateTerritoryOccupied(target);

        List<BuildingInstance> buildings =
                buildingInstanceRepository.findByTerritoryId(territoryId).stream()
                        .filter(b -> !b.isDestroyed())
                        .filter(b -> b.getZone() != null && b.getZone() >= 1 && b.getZone() <= 3)
                        .toList();
        BuildingLevelSpecResolver resolver =
                BuildingLevelSpecResolver.of(buildings, buildingLevelSpecRepository);
        LocalDateTime now = LocalDateTime.now();

        List<SiegeTargetResponse.TargetBuilding> targetBuildings =
                buildings.stream().map(b -> toTargetBuilding(b, resolver, now)).toList();
        return new SiegeTargetResponse(
                target.getId(),
                target.getCoordX(),
                target.getCoordY(),
                buildZoneHps(buildings, resolver),
                targetBuildings);
    }

    private SiegeTargetResponse.TargetBuilding toTargetBuilding(
            BuildingInstance b, BuildingLevelSpecResolver resolver, LocalDateTime now) {
        return new SiegeTargetResponse.TargetBuilding(
                b.getId(),
                b.getBuildingType().getName(),
                b.getBuildingType().getDisplayName(),
                b.getZone(),
                b.getHp(),
                resolver.maxHp(b),
                b.getPosX(),
                b.getPosY(),
                b.getBuildingType().getWidth(),
                b.getBuildingType().getHeight(),
                b.isUnderConstruction(now));
    }

    private List<SiegeTargetResponse.ZoneHp> buildZoneHps(
            List<BuildingInstance> buildings, BuildingLevelSpecResolver resolver) {
        List<SiegeTargetResponse.ZoneHp> zones = new ArrayList<>();
        for (int zone = 1; zone <= 3; zone++) {
            final int currentZone = zone;
            List<BuildingInstance> inZone =
                    buildings.stream().filter(b -> b.getZone() == currentZone).toList();
            int currentHp = inZone.stream().mapToInt(BuildingInstance::getHp).sum();
            int maxHp = inZone.stream().mapToInt(resolver::maxHp).sum();
            zones.add(new SiegeTargetResponse.ZoneHp(currentZone, currentHp, maxHp, inZone.size()));
        }
        return zones;
    }

    @Transactional
    public ProduceUnitResponse produceUnit(Long userId, ProduceUnitRequest request) {
        UnitType unitType = findUnitTypeOrThrow(request.unitTypeId());
        int level = levelOf(request.level());
        validateLevelResearched(userId, unitType.getId(), level);

        LocationRef loc =
                resolveOwnedLocation(userId, request.locationId(), request.locationType());
        // 레벨 2+는 레벨 스펙의 요구 병영 레벨·훈련 식량을 따른다. 레벨 1은 UnitType 기본값.
        UnitTypeLevelSpec spec = level > 1 ? findLevelSpecOrThrow(unitType.getId(), level) : null;
        validateProductionLocation(
                loc,
                spec != null ? spec.getRequiredBarracksLevel() : unitType.getLevel(),
                request.quantity());

        int gpCost = unitType.getCostGp() * request.quantity();
        int foodPerUnit = spec != null ? spec.getTrainCostFood() : unitType.getFoodCost();
        int foodCost = foodPerUnit * request.quantity();
        List<BuildingInstance> storages = findLocationStoragesWithLock(loc);
        int gpRemaining = chargeLocationGpAndFood(storages, gpCost, foodCost);

        addReadyIdleAtLocation(userId, unitType, level, loc, request.quantity());
        log.info(
                "유닛 생산 완료. userId={}, unitTypeId={}, level={}, quantity={}, {}={}",
                userId,
                unitType.getId(),
                level,
                request.quantity(),
                loc.type(),
                loc.id());
        return ProduceUnitResponse.of(unitType, request.quantity(), gpRemaining);
    }

    @Transactional
    public DeployUnitResponse deployUnit(Long userId, DeployUnitRequest request) {
        Territory territory = findOwnedTerritoryOrThrow(request.territoryId(), userId);
        BuildingInstance building =
                resolveGarrisonBuilding(request.buildingId(), territory.getId());
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
                userId, idle.getUnitType(), level, source, territory, building, request.quantity());
        return new DeployUnitResponse(request.quantity(), request.territoryId());
    }

    // 주둔 대상 건물: 그 영토에 있고 파괴되지 않은 건물이어야 한다.
    private BuildingInstance resolveGarrisonBuilding(Long buildingId, Long territoryId) {
        BuildingInstance building =
                buildingInstanceRepository
                        .findById(buildingId)
                        .orElseThrow(() -> new CustomException(ErrorCode.BUILDING_NOT_FOUND));
        if (building.getTerritory() == null
                || !building.getTerritory().getId().equals(territoryId)
                || building.isDestroyed()) {
            throw new CustomException(ErrorCode.BUILDING_NOT_FOUND);
        }
        return building;
    }

    // 건물별 주둔 수용량(레벨당): 성 5 · 타워 3 · 방벽 2 · 숙소 5. 그 외 건물은 주둔 불가(0).
    // 값은 관리자 밸런스 설정으로 덮어쓸 수 있다(없으면 MilitaryPolicy 기본값).
    private void validateGarrisonCapacity(BuildingInstance building, int quantity) {
        int perLevel =
                switch (building.getBuildingType().getName()) {
                    case "CASTLE" ->
                            balanceConfig.getInt(
                                    BalanceConfig.KEY_GARRISON_CAP_CASTLE,
                                    MilitaryPolicy.GARRISON_CAP_CASTLE);
                    case "RESIDENCE" ->
                            balanceConfig.getInt(
                                    BalanceConfig.KEY_GARRISON_CAP_RESIDENCE,
                                    MilitaryPolicy.GARRISON_CAP_RESIDENCE);
                    case "TOWER" ->
                            balanceConfig.getInt(
                                    BalanceConfig.KEY_GARRISON_CAP_TOWER,
                                    MilitaryPolicy.GARRISON_CAP_TOWER);
                    case "WALL" ->
                            balanceConfig.getInt(
                                    BalanceConfig.KEY_GARRISON_CAP_WALL,
                                    MilitaryPolicy.GARRISON_CAP_WALL);
                    default -> 0;
                };
        int capacity = perLevel * building.getLevel();
        int current =
                nullSafe(unitInstanceRepository.sumQuantityByDeployedBuildingId(building.getId()));
        if (current + quantity > capacity) {
            throw new CustomException(ErrorCode.UNIT_CAPACITY_EXCEEDED);
        }
    }

    @Transactional
    public RecallUnitResponse recallUnit(Long userId, RecallUnitRequest request) {
        findOwnedTerritoryOrThrow(request.territoryId(), userId);
        List<UnitInstance> deployedStacks =
                unitInstanceRepository.findDeployedAtTerritory(
                        userId,
                        request.unitTypeId(),
                        levelOf(request.level()),
                        request.territoryId());
        int recalled = recallFromDeployed(deployedStacks, request.quantity());
        int remaining = deployedStacks.stream().mapToInt(UnitInstance::getQuantity).sum();
        return new RecallUnitResponse(recalled, remaining);
    }

    @Transactional
    public MoveUnitResponse moveUnit(Long userId, MoveUnitRequest request) {
        LocationRef source =
                resolveOwnedLocation(
                        userId, request.sourceLocationId(), request.sourceLocationType());
        LocationRef dest =
                resolveOwnedLocation(userId, request.destLocationId(), request.destLocationType());
        validateDifferentLocation(source, dest);

        int level = levelOf(request.level());
        UnitInstance idle =
                findReadyIdleAtLocationOrThrow(
                        userId, request.unitTypeId(), level, source, request.quantity());
        validateUnitCapacityAtLocation(dest, request.quantity());

        int gpCost = MilitaryPolicy.UNIT_MOVE_COST_GP * request.quantity();
        List<BuildingInstance> storages = findLocationStoragesWithLock(source);
        int gpRemaining = chargeLocationGp(storages, gpCost);

        idle.subtractQuantity(request.quantity());
        LocalDateTime completeAt =
                LocalDateTime.now().plusMinutes(MilitaryPolicy.UNIT_MOVE_MINUTES);
        saveInTransitUnit(userId, idle.getUnitType(), level, dest, request.quantity(), completeAt);
        log.info(
                "유닛 이동 시작. userId={}, unitTypeId={}, quantity={}, {}={} -> {}={}",
                userId,
                request.unitTypeId(),
                request.quantity(),
                source.type(),
                source.id(),
                dest.type(),
                dest.id());
        return new MoveUnitResponse(request.quantity(), gpRemaining, completeAt);
    }

    @Transactional
    public DeclareSiegeResponse declareSiege(Long userId, DeclareSiegeRequest request) {
        Territory target = findTerritoryOrThrow(request.targetTerritoryId());
        validateNotOwnTerritory(target, userId);
        validateTerritoryOccupied(target);
        validateNotProtected(target);
        validateAttackCooldown(request.targetTerritoryId(), userId);
        validateZoneCleared(request.targetTerritoryId(), userId, request.attackZone());

        validateAttackerForces(userId, request.forces());
        validateSiegeStructures(request.structures(), target);
        validateForcesWithinStagingCapacity(request.forces(), request.structures());

        AttackToken token = findAttackTokenOrThrow(userId);
        BuildingInstance targetBuilding = resolveTargetBuilding(request.targetBuildingId());
        validateTargetBuilding(target, request.attackZone(), targetBuilding);
        consumeAttackToken(token, targetBuilding);

        User attacker = findUserOrThrow(userId);
        SiegeEvent siege = buildSiegeEvent(attacker, target, targetBuilding, request);
        siegeEventRepository.save(siege);
        commitAttackerForces(siege, userId, request.forces());
        chargeVaultForStructures(userId, request.structures());
        saveSiegeStructures(siege, request.structures());

        // 방어자 알림 목록에 피습 기록(배지는 /sub/user/{id}/notification 로 동시 갱신).
        notificationService.sendNotification(
                target.getOwner().getId(),
                NotificationLog.NotificationType.SIEGE_ALERT,
                attacker.getNickname()
                        + "님이 ("
                        + target.getCoordX()
                        + ", "
                        + target.getCoordY()
                        + ") 영토를 공격했습니다. (Zone "
                        + request.attackZone()
                        + ")");

        int remaining = targetBuilding == null ? token.getNormalCount() : token.getPrecisionCount();

        final long finalSiegeId = siege.getId();
        final long finalTerritoryId = target.getId();
        final int finalCoordX = target.getCoordX();
        final int finalCoordY = target.getCoordY();
        final int finalAttackZone = request.attackZone();
        final long finalAttackerId = userId;
        final String finalAttackerNickname = attacker.getNickname();
        final long finalDefenderId = target.getOwner().getId();
        final String finalDefenderNickname = target.getOwner().getNickname();
        final LocalDateTime finalResolveAt = siege.getResolveAt();

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        messagingTemplate.convertAndSend(
                                "/sub/user/" + finalDefenderId + "/siege-alert",
                                new SiegeAlert(
                                        finalSiegeId,
                                        "DECLARED",
                                        finalTerritoryId,
                                        finalCoordX,
                                        finalCoordY,
                                        finalAttackZone,
                                        finalAttackerId,
                                        finalAttackerNickname,
                                        finalDefenderId,
                                        finalDefenderNickname,
                                        finalResolveAt,
                                        null,
                                        null));
                    }
                });

        log.info(
                "공성전 선언. siegeId={}, attackerId={}, targetTerritoryId={}",
                siege.getId(),
                userId,
                target.getId());
        return new DeclareSiegeResponse(siege.getId(), siege.getResolveAt(), remaining);
    }

    public SiegeResultResponse getSiegeResult(Long userId, Long siegeId) {
        SiegeEvent siege = findSiegeOrThrow(siegeId);
        validateSiegeParticipant(siege, userId);
        SiegeResult result =
                siegeResultRepository
                        .findBySiegeId(siegeId)
                        .orElseThrow(() -> new CustomException(ErrorCode.SIEGE_RESULT_NOT_FOUND));
        return SiegeResultResponse.of(siege, result);
    }

    // 정찰: SCOUT 유닛 1기를 소모해 대상 영토 방어 병력의 총 수만 알아낸다.
    // 유닛 종류·Zone 분포는 공개하지 않는다(정보 비대칭).
    @Transactional
    public ScoutTerritoryResponse scoutTerritory(Long userId, Long territoryId) {
        Territory target = findTerritoryOrThrow(territoryId);
        validateScoutTarget(target, userId);
        consumeScoutUnit(userId);

        Long defenderId = target.getOwner().getId();
        int defenderTotalUnits = countDeployedUnits(defenderId, territoryId);
        return new ScoutTerritoryResponse(territoryId, defenderTotalUnits);
    }

    private void validateScoutTarget(Territory target, Long userId) {
        if (target.getOwner() == null || target.getOwner().getId().equals(userId)) {
            throw new CustomException(ErrorCode.SCOUT_INVALID_TARGET);
        }
    }

    private void consumeScoutUnit(Long userId) {
        UnitType scout =
                unitTypeRepository
                        .findByName(SCOUT_UNIT_NAME)
                        .orElseThrow(() -> new CustomException(ErrorCode.UNIT_TYPE_NOT_FOUND));
        if (nullSafe(unitInstanceRepository.sumReadyIdleQuantity(userId, scout.getId(), 1)) < 1) {
            throw new CustomException(ErrorCode.SCOUT_UNIT_REQUIRED);
        }
        deductReadyIdle(userId, scout.getId(), 1, 1);
    }

    private int countDeployedUnits(Long defenderId, Long territoryId) {
        return unitInstanceRepository
                .findByUserIdAndDeployedTerritoryId(defenderId, territoryId)
                .stream()
                .mapToInt(UnitInstance::getQuantity)
                .sum();
    }

    public UnitListResponse getUnitList(Long userId) {
        List<UnitInstance> instances = unitInstanceRepository.findByUserId(userId);
        return buildUnitListResponse(userId, instances);
    }

    // 훈련 가능한 유닛 종류 전체. 보유 여부와 무관 — 생산 UI가 첫 유닛도 고를 수 있게 한다.
    public List<UnitTypeCatalogResponse> getUnitTypeCatalog() {
        return unitTypeRepository.findAll().stream().map(UnitTypeCatalogResponse::from).toList();
    }

    // 특정 영토에 배치된 '내' 유닛을 타입별 합계로 반환한다(회수 UI용). 호출자 소유분만 → 정보 비대칭 유지.
    public List<GarrisonUnitResponse> getTerritoryGarrison(Long userId, Long territoryId) {
        Map<UnitType, Integer> byType = new LinkedHashMap<>();
        for (UnitInstance unit :
                unitInstanceRepository.findByUserIdAndDeployedTerritoryId(userId, territoryId)) {
            byType.merge(unit.getUnitType(), unit.getQuantity(), Integer::sum);
        }
        return byType.entrySet().stream()
                .map(
                        e ->
                                new GarrisonUnitResponse(
                                        e.getKey().getId(),
                                        e.getKey().getName(),
                                        e.getKey().getDisplayName(),
                                        e.getKey().getIcon(),
                                        e.getKey().getColorHex(),
                                        e.getValue()))
                .toList();
    }

    public SiegeEventListResponse getSiegeEvents(String statusParam, Pageable pageable) {
        SiegeEvent.SiegeStatus status = parseSiegeStatus(statusParam);
        Page<SiegeEvent> page = siegeEventRepository.findByStatus(status, pageable);
        List<SiegeEventListResponse.SiegeDto> dtos =
                page.getContent().stream().map(this::toSiegeDto).toList();
        return new SiegeEventListResponse(page.getTotalElements(), dtos);
    }

    public MySiegeHistoryResponse getMySiegeHistory(
            Long userId, String resultFilter, Pageable pageable) {
        Page<SiegeEvent> page =
                siegeEventRepository.findMyHistory(
                        userId, SiegeEvent.SiegeStatus.RESOLVED, pageable);
        return buildHistoryResponse(userId, page, resultFilter);
    }

    // 영토 상실(토지세 미납·점유 만료) 시 그 영토에 귀속·배치됐던 소유자 유닛을 홈 아일랜드로 퇴각시킨다.
    // 섬 수용량을 넘는 분은 소멸한다. 섬이 없으면 전부 소멸한다.
    @EventListener
    @Transactional
    public void handleTerritoryLost(TerritoryLostEvent event) {
        List<UnitInstance> units =
                unitInstanceRepository.findByOwnerAndTerritoryAssociation(
                        event.formerOwnerId(), event.territoryId());
        if (units.isEmpty()) {
            return;
        }
        homeIslandRepository
                .findByUserId(event.formerOwnerId())
                .ifPresentOrElse(
                        island -> retreatUnitsToIsland(event.formerOwnerId(), island, units),
                        () -> unitInstanceRepository.deleteAll(units));
    }

    // 공성 중 주둔 건물(성 제외)이 파괴되면 그 건물 주둔 방어 유닛을 홈 아일랜드로 퇴각시킨다.
    @EventListener
    @Transactional
    public void handleGarrisonBuildingDestroyed(GarrisonBuildingDestroyedEvent event) {
        List<UnitInstance> garrison =
                unitInstanceRepository.findByDeployedBuildingId(event.buildingId());
        if (garrison.isEmpty()) {
            return;
        }
        homeIslandRepository
                .findByUserId(event.defenderId())
                .ifPresentOrElse(
                        island -> retreatUnitsToIsland(event.defenderId(), island, garrison),
                        () -> unitInstanceRepository.deleteAll(garrison));
    }

    private void retreatUnitsToIsland(Long userId, HomeIsland island, List<UnitInstance> units) {
        int free =
                Math.max(
                        0,
                        islandCapacity(island.getId())
                                - nullSafe(
                                        unitInstanceRepository.sumQuantityByHomeIslandId(
                                                island.getId())));
        for (UnitInstance unit : units) {
            // 이미 섬 귀속(섬→영토 배치분)이면 수용량에 이미 포함 → 전량 유지, 아니면 남은 슬롯까지만.
            boolean alreadyOnIsland =
                    unit.getHomeIsland() != null
                            && unit.getHomeIsland().getId().equals(island.getId());
            int accepted =
                    alreadyOnIsland ? unit.getQuantity() : Math.min(unit.getQuantity(), free);
            if (!alreadyOnIsland) {
                free -= accepted;
            }
            if (accepted > 0) {
                addIslandIdle(userId, unit.getUnitType(), unit.getLevel(), island, accepted);
            }
            unitInstanceRepository.delete(unit);
        }
        log.info(
                "영토 상실 유닛 섬 퇴각. userId={}, islandId={}, stacks={}",
                userId,
                island.getId(),
                units.size());
    }

    private void addIslandIdle(
            Long userId, UnitType unitType, int level, HomeIsland island, int quantity) {
        unitInstanceRepository
                .findReadyIdleAtIsland(userId, unitType.getId(), level, island.getId())
                .ifPresentOrElse(
                        e -> e.addQuantity(quantity),
                        () ->
                                unitInstanceRepository.save(
                                        UnitInstance.builder()
                                                .user(findUserOrThrow(userId))
                                                .unitType(unitType)
                                                .quantity(quantity)
                                                .homeIsland(island)
                                                .build()));
    }

    private int islandCapacity(Long islandId) {
        int castleLevel = buildingInstanceRepository.findCastleLevelByIslandId(islandId).orElse(0);
        int residence =
                nullSafe(
                        buildingInstanceRepository.sumResidenceCapacityByIslandId(
                                islandId, LocalDateTime.now()));
        return MilitaryPolicy.castleUnitSlots(castleLevel) + residence;
    }

    // --- private helpers ---

    private UnitType findUnitTypeOrThrow(Long unitTypeId) {
        return unitTypeRepository
                .findById(unitTypeId)
                .orElseThrow(() -> new CustomException(ErrorCode.UNIT_TYPE_NOT_FOUND));
    }

    /** 유닛이 귀속·생산되는 위치. 영토 또는 섬 중 하나. */
    private record LocationRef(
            LocationType type, Long id, Territory territory, HomeIsland island) {}

    private LocationRef resolveOwnedLocation(Long userId, Long locationId, LocationType type) {
        if (type == LocationType.TERRITORY) {
            return new LocationRef(
                    type, locationId, findOwnedTerritoryOrThrow(locationId, userId), null);
        }
        return new LocationRef(type, locationId, null, findOwnedIslandOrThrow(locationId, userId));
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

    private void validateBarracksAtLocation(LocationRef loc, int requiredLevel) {
        boolean exists =
                loc.type() == LocationType.TERRITORY
                        ? buildingInstanceRepository.existsActiveBarracksByTerritoryId(loc.id())
                        : buildingInstanceRepository.existsActiveBarracksByIslandId(loc.id());
        if (!exists) {
            throw new CustomException(ErrorCode.NO_BARRACKS);
        }
        int maxLevel =
                (loc.type() == LocationType.TERRITORY
                                ? buildingInstanceRepository.findMaxBarracksLevelByTerritoryId(
                                        loc.id())
                                : buildingInstanceRepository.findMaxBarracksLevelByIslandId(
                                        loc.id()))
                        .orElse(0);
        if (maxLevel < requiredLevel) {
            throw new CustomException(ErrorCode.BARRACKS_LEVEL_INSUFFICIENT);
        }
    }

    private void validateProductionLocation(LocationRef loc, int requiredLevel, int quantity) {
        LocalDateTime now = LocalDateTime.now();
        MilitaryLocationSummary summary =
                loc.type() == LocationType.TERRITORY
                        ? buildingInstanceRepository.findMilitaryLocationSummaryByTerritoryId(
                                loc.id(), now)
                        : buildingInstanceRepository.findMilitaryLocationSummaryByIslandId(
                                loc.id(), now);
        int barracksLevel = nullSafe(summary.getMaxBarracksLevel());
        if (barracksLevel == 0) {
            throw new CustomException(ErrorCode.NO_BARRACKS);
        }
        if (barracksLevel < requiredLevel) {
            throw new CustomException(ErrorCode.BARRACKS_LEVEL_INSUFFICIENT);
        }

        int current =
                nullSafe(
                        loc.type() == LocationType.TERRITORY
                                ? unitInstanceRepository.sumQuantityByHomeTerritoryId(loc.id())
                                : unitInstanceRepository.sumQuantityByHomeIslandId(loc.id()));
        int capacity =
                MilitaryPolicy.castleUnitSlots(nullSafe(summary.getCastleLevel()))
                        + nullSafe(summary.getResidenceCapacity());
        if (current + quantity > capacity) {
            throw new CustomException(ErrorCode.UNIT_CAPACITY_EXCEEDED);
        }
    }

    private void validateUnitCapacityAtLocation(LocationRef loc, int quantity) {
        int current =
                nullSafe(
                        loc.type() == LocationType.TERRITORY
                                ? unitInstanceRepository.sumQuantityByHomeTerritoryId(loc.id())
                                : unitInstanceRepository.sumQuantityByHomeIslandId(loc.id()));
        if (current + quantity > locationCapacity(loc)) {
            throw new CustomException(ErrorCode.UNIT_CAPACITY_EXCEEDED);
        }
    }

    private int locationCapacity(LocationRef loc) {
        int castleLevel =
                (loc.type() == LocationType.TERRITORY
                                ? buildingInstanceRepository.findCastleLevelByTerritoryId(loc.id())
                                : buildingInstanceRepository.findCastleLevelByIslandId(loc.id()))
                        .orElse(0);
        int residence =
                nullSafe(
                        loc.type() == LocationType.TERRITORY
                                ? buildingInstanceRepository.sumResidenceCapacityByTerritoryId(
                                        loc.id(), LocalDateTime.now())
                                : buildingInstanceRepository.sumResidenceCapacityByIslandId(
                                        loc.id(), LocalDateTime.now()));
        return MilitaryPolicy.castleUnitSlots(castleLevel) + residence;
    }

    private List<BuildingInstance> findLocationStoragesWithLock(LocationRef loc) {
        return loc.type() == LocationType.TERRITORY
                ? buildingInstanceRepository.findStorageBuildingsByTerritoryIdWithLock(loc.id())
                : buildingInstanceRepository.findStorageBuildingsByIslandIdWithLock(loc.id());
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

    private void validateDifferentLocation(LocationRef source, LocationRef dest) {
        if (source.type() == dest.type() && source.id().equals(dest.id())) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }

    private int nullSafe(Integer value) {
        return value != null ? value : 0;
    }

    /** 요청의 레벨 — null이면 기본 레벨 1. */
    private int levelOf(Integer level) {
        return level != null ? level : 1;
    }

    /** 계정에 해금된 레벨(연구 완료분)을 넘는 레벨은 생산할 수 없다. */
    private void validateLevelResearched(Long userId, Long unitTypeId, int level) {
        if (level <= 1) return;
        int researched =
                unitResearchRepository
                        .findByUserIdAndUnitTypeId(userId, unitTypeId)
                        .map(
                                r -> {
                                    r.applyCompletionIfDue(LocalDateTime.now());
                                    return r.getResearchedLevel();
                                })
                        .orElse(1);
        if (level > researched) {
            throw new CustomException(ErrorCode.UNIT_LEVEL_NOT_RESEARCHED);
        }
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
            Territory deployed,
            BuildingInstance deployedBuilding,
            LocalDateTime moveCompleteAt) {
        User user = findUserOrThrow(userId);
        UnitInstance.UnitInstanceBuilder builder =
                UnitInstance.builder()
                        .user(user)
                        .unitType(unitType)
                        .quantity(quantity)
                        .level(level)
                        .moveCompleteAt(moveCompleteAt);
        if (home.type() == LocationType.TERRITORY) {
            builder.homeTerritory(home.territory());
        } else {
            builder.homeIsland(home.island());
        }
        UnitInstance instance = builder.build();
        if (deployed != null) {
            instance.deployTo(deployed, deployedBuilding);
        }
        return instance;
    }

    /** 대기(ready idle) 스택에 병합한다 — (귀속지·레벨)이 같은 스택으로. 없으면 새로 만든다. */
    private void addReadyIdleAtLocation(
            Long userId, UnitType unitType, int level, LocationRef loc, int quantity) {
        findReadyIdleAtLocation(userId, unitType.getId(), level, loc)
                .ifPresentOrElse(
                        e -> e.addQuantity(quantity),
                        () ->
                                unitInstanceRepository.save(
                                        newUnitAtLocation(
                                                userId, unitType, level, loc, quantity, null, null,
                                                null)));
    }

    private Optional<UnitInstance> findReadyIdleAtLocation(
            Long userId, Long unitTypeId, int level, LocationRef loc) {
        return loc.type() == LocationType.TERRITORY
                ? unitInstanceRepository.findReadyIdleAtTerritory(
                        userId, unitTypeId, level, loc.id())
                : unitInstanceRepository.findReadyIdleAtIsland(userId, unitTypeId, level, loc.id());
    }

    private UnitInstance findReadyIdleAtLocationOrThrow(
            Long userId, Long unitTypeId, int level, LocationRef loc, int required) {
        UnitInstance idle =
                findReadyIdleAtLocation(userId, unitTypeId, level, loc)
                        .orElseThrow(() -> new CustomException(ErrorCode.INSUFFICIENT_UNITS));
        if (idle.getQuantity() < required) {
            throw new CustomException(ErrorCode.INSUFFICIENT_UNITS);
        }
        return idle;
    }

    /** 배치(deployed) 스택에 병합한다 — (귀속지·레벨·배치건물)이 같은 스택으로. */
    private void addDeployedUnits(
            Long userId,
            UnitType unitType,
            int level,
            LocationRef source,
            Territory territory,
            BuildingInstance building,
            int quantity) {
        Optional<UnitInstance> existing =
                source.type() == LocationType.TERRITORY
                        ? unitInstanceRepository.findDeployedFromTerritory(
                                userId, unitType.getId(), level, source.id(), building.getId())
                        : unitInstanceRepository.findDeployedFromIsland(
                                userId, unitType.getId(), level, source.id(), building.getId());
        existing.ifPresentOrElse(
                e -> e.addQuantity(quantity),
                () ->
                        unitInstanceRepository.save(
                                newUnitAtLocation(
                                        userId, unitType, level, source, quantity, territory,
                                        building, null)));
    }

    /** 배치 스택들에서 회수해 각자의 귀속지 대기 스택으로 되돌린다. */
    private int recallFromDeployed(List<UnitInstance> deployedStacks, int quantity) {
        int available = deployedStacks.stream().mapToInt(UnitInstance::getQuantity).sum();
        if (available < quantity) {
            throw new CustomException(ErrorCode.INSUFFICIENT_UNITS);
        }
        int remaining = quantity;
        for (UnitInstance deployed : deployedStacks) {
            if (remaining == 0) break;
            int take = Math.min(remaining, deployed.getQuantity());
            deployed.subtractQuantity(take);
            returnToHomeIdle(deployed, take);
            remaining -= take;
        }
        return quantity;
    }

    private void returnToHomeIdle(UnitInstance deployed, int quantity) {
        Long userId = deployed.getUser().getId();
        UnitType unitType = deployed.getUnitType();
        int level = deployed.getLevel();
        if (deployed.getHomeTerritory() != null) {
            Territory home = deployed.getHomeTerritory();
            unitInstanceRepository
                    .findReadyIdleAtTerritory(userId, unitType.getId(), level, home.getId())
                    .ifPresentOrElse(
                            e -> e.addQuantity(quantity),
                            () ->
                                    unitInstanceRepository.save(
                                            UnitInstance.builder()
                                                    .user(deployed.getUser())
                                                    .unitType(unitType)
                                                    .quantity(quantity)
                                                    .level(level)
                                                    .homeTerritory(home)
                                                    .build()));
        } else {
            HomeIsland home = deployed.getHomeIsland();
            unitInstanceRepository
                    .findReadyIdleAtIsland(userId, unitType.getId(), level, home.getId())
                    .ifPresentOrElse(
                            e -> e.addQuantity(quantity),
                            () ->
                                    unitInstanceRepository.save(
                                            UnitInstance.builder()
                                                    .user(deployed.getUser())
                                                    .unitType(unitType)
                                                    .quantity(quantity)
                                                    .level(level)
                                                    .homeIsland(home)
                                                    .build()));
        }
    }

    private void saveInTransitUnit(
            Long userId,
            UnitType unitType,
            int level,
            LocationRef dest,
            int quantity,
            LocalDateTime completeAt) {
        unitInstanceRepository.save(
                newUnitAtLocation(userId, unitType, level, dest, quantity, null, null, completeAt));
    }

    private void validateReadyIdleAvailable(Long userId, Long unitTypeId, int level, int quantity) {
        if (nullSafe(unitInstanceRepository.sumReadyIdleQuantity(userId, unitTypeId, level))
                < quantity) {
            throw new CustomException(ErrorCode.INSUFFICIENT_UNITS);
        }
    }

    // 공격 병력 가용성 검증 — 각 유닛 타입의 대기 풀 수량이 충분한지. 토큰 소모·저장 전에 확인한다.
    private void validateAttackerForces(Long userId, List<DeclareSiegeRequest.ForceEntry> forces) {
        for (DeclareSiegeRequest.ForceEntry entry : forces) {
            validateReadyIdleAvailable(
                    userId, entry.unitTypeId(), levelOf(entry.level()), entry.quantity());
        }
    }

    // 공격 병력 커밋: 각 유닛 타입 수량을 대기 풀에서 차감(락)하고 SiegeForce로 기록한다.
    // 판정 시점(30분 뒤)에 이 기록으로 전투를 계산한다.
    private void commitAttackerForces(
            SiegeEvent siege, Long userId, List<DeclareSiegeRequest.ForceEntry> forces) {
        for (DeclareSiegeRequest.ForceEntry entry : forces) {
            UnitType unitType = findUnitTypeOrThrow(entry.unitTypeId());
            int level = levelOf(entry.level());
            deductReadyIdle(userId, entry.unitTypeId(), level, entry.quantity());
            siegeForceRepository.save(
                    SiegeForce.builder()
                            .siege(siege)
                            .unitType(unitType)
                            .quantity(entry.quantity())
                            .level(level)
                            .build());
        }
    }

    // 공성 건물 배치 검증: 주둔지 1개 이상 + 개수 상한 + 좌표가 대상 인접 타일 + 좌표 중복 금지.
    private void validateSiegeStructures(
            List<DeclareSiegeRequest.StructureEntry> structures, Territory target) {
        if (structures.size() > MilitaryPolicy.SIEGE_STRUCTURE_MAX) {
            throw new CustomException(ErrorCode.SIEGE_STRUCTURE_LIMIT_EXCEEDED);
        }
        boolean hasStaging =
                structures.stream().anyMatch(s -> s.type() == SiegeStructureType.STAGING);
        if (!hasStaging) {
            throw new CustomException(ErrorCode.SIEGE_STAGING_REQUIRED);
        }
        Set<String> seen = new HashSet<>();
        for (DeclareSiegeRequest.StructureEntry entry : structures) {
            validateAdjacentTile(entry, target);
            if (!seen.add(entry.coordX() + ":" + entry.coordY())) {
                throw new CustomException(ErrorCode.SIEGE_STRUCTURE_PLACEMENT_INVALID);
            }
        }
    }

    private void validateAdjacentTile(DeclareSiegeRequest.StructureEntry entry, Territory target) {
        int dx = Math.abs(entry.coordX() - target.getCoordX());
        int dy = Math.abs(entry.coordY() - target.getCoordY());
        boolean inGrid =
                entry.coordX() >= 0
                        && entry.coordX() < MilitaryPolicy.MAP_GRID_SIZE
                        && entry.coordY() >= 0
                        && entry.coordY() < MilitaryPolicy.MAP_GRID_SIZE;
        boolean adjacent =
                (dx != 0 || dy != 0)
                        && dx <= MilitaryPolicy.SIEGE_STRUCTURE_RANGE
                        && dy <= MilitaryPolicy.SIEGE_STRUCTURE_RANGE;
        if (!inGrid || !adjacent) {
            throw new CustomException(ErrorCode.SIEGE_STRUCTURE_PLACEMENT_INVALID);
        }
    }

    // 공격 병력 상한 = 주둔지 수 × 수용량. 커밋 병력 합이 이를 넘으면 거부.
    private void validateForcesWithinStagingCapacity(
            List<DeclareSiegeRequest.ForceEntry> forces,
            List<DeclareSiegeRequest.StructureEntry> structures) {
        long stagingCount =
                structures.stream().filter(s -> s.type() == SiegeStructureType.STAGING).count();
        int capacity = (int) stagingCount * MilitaryPolicy.STAGING_CAPACITY_PER;
        int totalForces = forces.stream().mapToInt(DeclareSiegeRequest.ForceEntry::quantity).sum();
        if (totalForces > capacity) {
            throw new CustomException(ErrorCode.SIEGE_FORCE_EXCEEDS_CAPACITY);
        }
    }

    private void chargeVaultForStructures(
            Long userId, List<DeclareSiegeRequest.StructureEntry> structures) {
        int totalCost =
                structures.stream().mapToInt(s -> MilitaryPolicy.structureCostGp(s.type())).sum();
        GlobalVault vault =
                globalVaultRepository
                        .findByIdWithLock(userId)
                        .orElseThrow(() -> new CustomException(ErrorCode.INSUFFICIENT_GP));
        if (vault.getStoredGp() < totalCost) {
            throw new CustomException(ErrorCode.INSUFFICIENT_GP);
        }
        vault.withdrawGp(totalCost);
    }

    private void saveSiegeStructures(
            SiegeEvent siege, List<DeclareSiegeRequest.StructureEntry> structures) {
        for (DeclareSiegeRequest.StructureEntry entry : structures) {
            siegeStructureRepository.save(
                    SiegeStructure.builder()
                            .siege(siege)
                            .type(entry.type())
                            .coordX(entry.coordX())
                            .coordY(entry.coordY())
                            .build());
        }
    }

    private void deductReadyIdle(Long userId, Long unitTypeId, int level, int quantity) {
        int remaining = quantity;
        for (UnitInstance stack :
                unitInstanceRepository.findReadyIdleByUserIdAndUnitTypeIdAndLevel(
                        userId, unitTypeId, level)) {
            if (remaining <= 0) break;
            int take = Math.min(stack.getQuantity(), remaining);
            stack.subtractQuantity(take);
            remaining -= take;
            if (stack.getQuantity() <= 0) {
                unitInstanceRepository.delete(stack);
            }
        }
    }

    private Territory findOwnedTerritoryOrThrow(Long territoryId, Long userId) {
        Territory territory = findTerritoryOrThrow(territoryId);
        if (territory.getOwner() == null || !territory.getOwner().getId().equals(userId)) {
            throw new CustomException(ErrorCode.NOT_TERRITORY_OWNER);
        }
        return territory;
    }

    private Territory findTerritoryOrThrow(Long territoryId) {
        return territoryRepository
                .findById(territoryId)
                .orElseThrow(() -> new CustomException(ErrorCode.TERRITORY_NOT_FOUND));
    }

    private void validateNotOwnTerritory(Territory territory, Long userId) {
        if (territory.getOwner() != null && territory.getOwner().getId().equals(userId)) {
            throw new CustomException(ErrorCode.CANNOT_ATTACK_OWN_TERRITORY);
        }
    }

    private void validateTerritoryOccupied(Territory territory) {
        if (territory.getStatus() != Territory.TerritoryStatus.OCCUPIED) {
            throw new CustomException(ErrorCode.TERRITORY_NOT_OCCUPIED);
        }
    }

    private void validateNotProtected(Territory territory) {
        if (territory.getProtectedUntil() != null
                && LocalDateTime.now().isBefore(territory.getProtectedUntil())) {
            throw new CustomException(ErrorCode.TERRITORY_PROTECTED);
        }
    }

    private void validateAttackCooldown(Long territoryId, Long attackerId) {
        List<SiegeEvent> recent =
                siegeEventRepository.findRecentByTerritoryAndAttacker(
                        territoryId, attackerId, SiegeEvent.SiegeStatus.RESOLVED);
        if (recent.isEmpty()) {
            return;
        }
        SiegeEvent last = recent.get(0);
        boolean inCooldown =
                siegeResultRepository
                        .findBySiegeId(last.getId())
                        .filter(r -> !r.getIsAttackerWin())
                        .map(
                                r ->
                                        last.getResolveAt()
                                                .plusHours(cooldownHours(r))
                                                .isAfter(LocalDateTime.now()))
                        .orElse(false);
        if (inCooldown) {
            throw new CustomException(ErrorCode.ATTACK_COOLDOWN);
        }
    }

    // 보급소로 완화된 쿨다운이 기록돼 있으면 그 값, 없으면(구 데이터) 기본 쿨다운.
    private int cooldownHours(SiegeResult result) {
        return result.getAppliedCooldownHours() != null
                ? result.getAppliedCooldownHours()
                : MilitaryPolicy.ATTACK_COOLDOWN_HOURS;
    }

    // 공략은 외곽(Zone 3) → 중심(Zone 1) 순. 안쪽 Zone은 바로 바깥 Zone(attackZone+1)을
    // 먼저 클리어해야 진입 가능하다. 최외곽(Zone 3)은 전제 없음.
    private void validateZoneCleared(Long territoryId, Long attackerId, int attackZone) {
        if (attackZone >= MilitaryPolicy.OUTERMOST_ZONE) {
            return;
        }
        List<SiegeEvent> prevZoneEvents =
                siegeEventRepository.findRecentByTerritoryAndAttacker(
                        territoryId, attackerId, SiegeEvent.SiegeStatus.RESOLVED);
        boolean cleared =
                prevZoneEvents.stream()
                        .filter(e -> e.getAttackZone() == attackZone + 1)
                        .anyMatch(
                                e ->
                                        siegeResultRepository
                                                .findBySiegeId(e.getId())
                                                .map(SiegeResult::getIsAttackerWin)
                                                .orElse(false));
        if (!cleared) {
            throw new CustomException(ErrorCode.ZONE_NOT_CLEARED);
        }
    }

    private AttackToken findAttackTokenOrThrow(Long userId) {
        return attackTokenRepository
                .findByUserIdWithLock(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.NO_ATTACK_TOKEN));
    }

    private BuildingInstance resolveTargetBuilding(Long targetBuildingId) {
        if (targetBuildingId == null) {
            return null;
        }
        return buildingInstanceRepository
                .findById(targetBuildingId)
                .orElseThrow(() -> new CustomException(ErrorCode.BUILDING_NOT_FOUND));
    }

    // 정밀 공격 대상 건물은 대상 영토의, 공격 구역(존)에 속한, 파괴되지 않은 건물이어야 한다.
    private void validateTargetBuilding(
            Territory target, Integer attackZone, BuildingInstance building) {
        if (building == null) {
            return;
        }
        boolean isSameTerritory =
                building.getTerritory() != null
                        && building.getTerritory().getId().equals(target.getId());
        boolean isSameZone = attackZone.equals(building.getZone());
        if (!isSameTerritory || !isSameZone || building.isDestroyed()) {
            throw new CustomException(ErrorCode.SIEGE_TARGET_BUILDING_INVALID);
        }
    }

    private void consumeAttackToken(AttackToken token, BuildingInstance targetBuilding) {
        if (targetBuilding == null) {
            if (token.getNormalCount() <= 0) {
                throw new CustomException(ErrorCode.NO_ATTACK_TOKEN);
            }
            token.consumeNormal();
        } else {
            if (token.getPrecisionCount() <= 0) {
                throw new CustomException(ErrorCode.NO_ATTACK_TOKEN);
            }
            token.consumePrecision();
        }
    }

    private User findUserOrThrow(Long userId) {
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    private SiegeEvent buildSiegeEvent(
            User attacker,
            Territory target,
            BuildingInstance targetBuilding,
            DeclareSiegeRequest request) {
        LocalDateTime now = LocalDateTime.now();
        return SiegeEvent.builder()
                .attacker(attacker)
                .defender(target.getOwner())
                .targetTerritory(target)
                .targetBuilding(targetBuilding)
                .attackZone(request.attackZone())
                .siegeStartAt(now)
                .resolveAt(now.plusMinutes(MilitaryPolicy.SIEGE_COUNTDOWN_MINUTES))
                .build();
    }

    private SiegeEvent findSiegeOrThrow(Long siegeId) {
        return siegeEventRepository
                .findById(siegeId)
                .orElseThrow(() -> new CustomException(ErrorCode.SIEGE_NOT_FOUND));
    }

    private void validateSiegeParticipant(SiegeEvent siege, Long userId) {
        boolean isParticipant =
                siege.getAttacker().getId().equals(userId)
                        || siege.getDefender().getId().equals(userId);
        if (!isParticipant) {
            throw new CustomException(ErrorCode.SIEGE_FORBIDDEN);
        }
    }

    /** 유저의 위치(소유 영토 + 홈 아일랜드)별로 유닛·수용량·저장 식량을 묶어 돌려준다. */
    private UnitListResponse buildUnitListResponse(Long userId, List<UnitInstance> instances) {
        Map<String, List<UnitInstance>> byLocation = new LinkedHashMap<>();
        for (UnitInstance inst : instances) {
            byLocation.computeIfAbsent(locationKey(inst), k -> new ArrayList<>()).add(inst);
        }

        List<UnitListResponse.LocationUnits> locations = new ArrayList<>();
        for (Territory t : territoryRepository.findByOwnerId(userId)) {
            LocationRef loc = new LocationRef(LocationType.TERRITORY, t.getId(), t, null);
            locations.add(
                    toLocationUnits(
                            loc,
                            t.getCoordX(),
                            t.getCoordY(),
                            byLocation.getOrDefault("T" + t.getId(), List.of())));
        }
        homeIslandRepository
                .findByUserId(userId)
                .ifPresent(
                        island -> {
                            LocationRef loc =
                                    new LocationRef(
                                            LocationType.ISLAND, island.getId(), null, island);
                            locations.add(
                                    toLocationUnits(
                                            loc,
                                            null,
                                            null,
                                            byLocation.getOrDefault(
                                                    "I" + island.getId(), List.of())));
                        });
        return new UnitListResponse(locations);
    }

    private String locationKey(UnitInstance inst) {
        return inst.getHomeTerritory() != null
                ? "T" + inst.getHomeTerritory().getId()
                : "I" + inst.getHomeIsland().getId();
    }

    private UnitListResponse.LocationUnits toLocationUnits(
            LocationRef loc, Integer coordX, Integer coordY, List<UnitInstance> units) {
        List<BuildingInstance> storages =
                loc.type() == LocationType.TERRITORY
                        ? buildingInstanceRepository.findStorageBuildingsByTerritoryId(loc.id())
                        : buildingInstanceRepository.findStorageBuildingsByIslandId(loc.id());
        Map<Long, List<UnitInstance>> byType = new LinkedHashMap<>();
        for (UnitInstance u : units) {
            byType.computeIfAbsent(u.getUnitType().getId(), k -> new ArrayList<>()).add(u);
        }
        List<UnitListResponse.UnitDto> unitDtos = new ArrayList<>();
        for (List<UnitInstance> group : byType.values()) {
            unitDtos.add(toUnitDto(group));
        }
        return new UnitListResponse.LocationUnits(
                loc.type().name(),
                loc.id(),
                coordX,
                coordY,
                locationCapacity(loc),
                StoragePolicy.totalFood(storages),
                unitDtos);
    }

    private UnitListResponse.UnitDto toUnitDto(List<UnitInstance> group) {
        UnitType ut = group.get(0).getUnitType();
        int total = group.stream().mapToInt(UnitInstance::getQuantity).sum();
        int deployed =
                group.stream()
                        .filter(u -> u.getDeployedTerritory() != null)
                        .mapToInt(UnitInstance::getQuantity)
                        .sum();
        int inTransit =
                group.stream()
                        .filter(UnitInstance::isInTransit)
                        .mapToInt(UnitInstance::getQuantity)
                        .sum();
        return new UnitListResponse.UnitDto(
                ut.getId(),
                ut.getName(),
                ut.getDisplayName(),
                ut.getIcon(),
                ut.getColorHex(),
                total,
                deployed,
                total - deployed - inTransit,
                inTransit,
                ut.getAttackPower(),
                ut.getDefensePower(),
                ut.getCostGp(),
                ut.getFoodCost(),
                ut.getBuildingDamage(),
                ut.getLevel());
    }

    private SiegeEvent.SiegeStatus parseSiegeStatus(String statusParam) {
        try {
            return SiegeEvent.SiegeStatus.valueOf(statusParam.toUpperCase());
        } catch (Exception e) {
            return SiegeEvent.SiegeStatus.PENDING;
        }
    }

    private SiegeEventListResponse.SiegeDto toSiegeDto(SiegeEvent siege) {
        BuildingInstance target = siege.getTargetBuilding();
        SiegeEventListResponse.TargetBuildingDto targetBuildingDto =
                target == null
                        ? null
                        : new SiegeEventListResponse.TargetBuildingDto(
                                target.getId(),
                                target.getBuildingType().getName(),
                                target.getBuildingType().getDisplayName());
        return new SiegeEventListResponse.SiegeDto(
                siege.getId(),
                siege.getStatus().name(),
                new SiegeEventListResponse.UserDto(
                        siege.getAttacker().getId(), siege.getAttacker().getNickname()),
                new SiegeEventListResponse.UserDto(
                        siege.getDefender().getId(), siege.getDefender().getNickname()),
                new SiegeEventListResponse.TerritoryDto(
                        siege.getTargetTerritory().getId(),
                        siege.getTargetTerritory().getCoordX(),
                        siege.getTargetTerritory().getCoordY()),
                siege.getAttackZone(),
                targetBuildingDto,
                siege.getSiegeStartAt(),
                siege.getResolveAt());
    }

    private MySiegeHistoryResponse buildHistoryResponse(
            Long userId, Page<SiegeEvent> page, String resultFilter) {
        List<MySiegeHistoryResponse.HistoryDto> history = new ArrayList<>();
        long wins = 0;
        long losses = 0;

        for (SiegeEvent siege : page.getContent()) {
            String role = siege.getAttacker().getId().equals(userId) ? "ATTACKER" : "DEFENDER";
            String result = resolveResult(siege, role);

            if ("WIN".equals(result)) wins++;
            else if ("LOSE".equals(result)) losses++;

            if (isResultFiltered(result, resultFilter)) {
                continue;
            }

            String grade =
                    siege.getTargetTerritory().getGrade() != null
                            ? siege.getTargetTerritory().getGrade().getGrade()
                            : null;
            history.add(
                    new MySiegeHistoryResponse.HistoryDto(
                            siege.getId(),
                            siege.getTargetTerritory().getId(),
                            grade,
                            role,
                            result,
                            siege.getResolveAt()));
        }
        return new MySiegeHistoryResponse(page.getTotalElements(), wins, losses, history);
    }

    private String resolveResult(SiegeEvent siege, String role) {
        return siegeResultRepository
                .findBySiegeId(siege.getId())
                .map(
                        r -> {
                            boolean attackerWin = Boolean.TRUE.equals(r.getIsAttackerWin());
                            return ("ATTACKER".equals(role) == attackerWin) ? "WIN" : "LOSE";
                        })
                .orElse("PENDING");
    }

    private boolean isResultFiltered(String result, String filter) {
        if (filter == null || "ALL".equalsIgnoreCase(filter)) {
            return false;
        }
        return !filter.equalsIgnoreCase(result);
    }
}
