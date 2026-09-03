package com.territorial.combat.domain.military.service;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.combat.domain.building.BuildingLevelSpecResolver;
import com.territorial.combat.domain.building.entity.BuildingInstance;
import com.territorial.combat.domain.building.entity.CombatUserSnapshot;
import com.territorial.combat.domain.building.repository.BuildingInstanceRepository;
import com.territorial.combat.domain.building.repository.BuildingLevelSpecRepository;
import com.territorial.combat.domain.building.repository.CombatUserSnapshotRepository;
import com.territorial.combat.domain.military.dto.MySiegeHistoryResponse;
import com.territorial.combat.domain.military.dto.ScoutTerritoryResponse;
import com.territorial.combat.domain.military.dto.SiegeEventListResponse;
import com.territorial.combat.domain.military.dto.SiegeResultResponse;
import com.territorial.combat.domain.military.dto.SiegeTargetResponse;
import com.territorial.combat.domain.military.entity.SiegeEvent;
import com.territorial.combat.domain.military.entity.SiegeResult;
import com.territorial.combat.domain.military.entity.UnitInstance;
import com.territorial.combat.domain.military.entity.UnitType;
import com.territorial.combat.domain.military.port.SiegeTerritoryPort;
import com.territorial.combat.domain.military.port.SiegeTerritoryPort.TerritoryCombatContext;
import com.territorial.combat.domain.military.repository.SiegeEventRepository;
import com.territorial.combat.domain.military.repository.SiegeResultRepository;
import com.territorial.combat.domain.military.repository.UnitInstanceRepository;
import com.territorial.combat.domain.military.repository.UnitTypeRepository;
import com.territorial.combat.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnBean(SiegeTerritoryPort.class)
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MilitaryQueryService {

    private static final String SCOUT_UNIT_NAME = "SCOUT";

    private final SiegeTerritoryPort territoryPort;
    private final BuildingInstanceRepository buildingInstanceRepository;
    private final BuildingLevelSpecRepository buildingLevelSpecRepository;
    private final CombatUserSnapshotRepository userSnapshotRepository;
    private final SiegeEventRepository siegeEventRepository;
    private final SiegeResultRepository siegeResultRepository;
    private final UnitTypeRepository unitTypeRepository;
    private final UnitInstanceRepository unitInstanceRepository;

    public SiegeTargetResponse getSiegeTarget(Long territoryId) {
        TerritoryCombatContext target = findOccupiedTerritory(territoryId);
        List<BuildingInstance> buildings =
                buildingInstanceRepository.findByTerritoryId(territoryId).stream()
                        .filter(building -> !building.isDestroyed())
                        .filter(
                                building ->
                                        building.getZone() != null
                                                && building.getZone() >= 1
                                                && building.getZone() <= 3)
                        .toList();
        BuildingLevelSpecResolver resolver =
                BuildingLevelSpecResolver.of(buildings, buildingLevelSpecRepository);
        LocalDateTime now = LocalDateTime.now();
        return new SiegeTargetResponse(
                target.territoryId(),
                target.coordX(),
                target.coordY(),
                buildZoneHps(buildings, resolver),
                buildings.stream()
                        .map(building -> toTargetBuilding(building, resolver, now))
                        .toList());
    }

    public SiegeResultResponse getSiegeResult(Long userId, Long siegeId) {
        SiegeEvent siege = findSiege(siegeId);
        if (!userId.equals(siege.getAttackerId()) && !userId.equals(siege.getDefenderId())) {
            throw new CustomException(ErrorCode.SIEGE_FORBIDDEN);
        }
        SiegeResult result =
                siegeResultRepository
                        .findBySiegeId(siegeId)
                        .orElseThrow(() -> new CustomException(ErrorCode.SIEGE_RESULT_NOT_FOUND));
        return SiegeResultResponse.of(siege, result);
    }

    @Transactional
    public ScoutTerritoryResponse scoutTerritory(Long userId, Long territoryId) {
        TerritoryCombatContext target = findTerritory(territoryId);
        if (target.ownerId() == null || userId.equals(target.ownerId())) {
            throw new CustomException(ErrorCode.SCOUT_INVALID_TARGET);
        }
        consumeScoutUnit(userId);
        int defenderTotalUnits =
                unitInstanceRepository
                        .findByUserIdAndDeployedTerritoryId(target.ownerId(), territoryId)
                        .stream()
                        .mapToInt(UnitInstance::getQuantity)
                        .sum();
        return new ScoutTerritoryResponse(territoryId, defenderTotalUnits);
    }

    public SiegeEventListResponse getSiegeEvents(String statusParam, Pageable pageable) {
        SiegeEvent.SiegeStatus status = parseSiegeStatus(statusParam);
        Page<SiegeEvent> page = siegeEventRepository.findByStatus(status, pageable);
        return new SiegeEventListResponse(
                page.getTotalElements(), page.getContent().stream().map(this::toSiegeDto).toList());
    }

    public MySiegeHistoryResponse getMySiegeHistory(
            Long userId, String resultFilter, Pageable pageable) {
        Page<SiegeEvent> page =
                siegeEventRepository.findMyHistory(
                        userId, SiegeEvent.SiegeStatus.RESOLVED, pageable);
        List<MySiegeHistoryResponse.HistoryDto> history = new ArrayList<>();
        long wins = 0;
        long losses = 0;
        for (SiegeEvent siege : page.getContent()) {
            String role = userId.equals(siege.getAttackerId()) ? "ATTACKER" : "DEFENDER";
            String result = resolveResult(siege, role);
            if ("WIN".equals(result)) {
                wins++;
            } else if ("LOSE".equals(result)) {
                losses++;
            }
            if (isResultFiltered(result, resultFilter)) {
                continue;
            }
            history.add(
                    new MySiegeHistoryResponse.HistoryDto(
                            siege.getId(),
                            siege.getTargetTerritoryId(),
                            findTerritory(siege.getTargetTerritoryId()).grade(),
                            role,
                            result,
                            siege.getResolveAt()));
        }
        return new MySiegeHistoryResponse(page.getTotalElements(), wins, losses, history);
    }

    private TerritoryCombatContext findTerritory(Long territoryId) {
        return territoryPort
                .findById(territoryId)
                .orElseThrow(() -> new CustomException(ErrorCode.TERRITORY_NOT_FOUND));
    }

    private TerritoryCombatContext findOccupiedTerritory(Long territoryId) {
        TerritoryCombatContext target = findTerritory(territoryId);
        if (!target.occupied()) {
            throw new CustomException(ErrorCode.TERRITORY_NOT_OCCUPIED);
        }
        return target;
    }

    private SiegeEvent findSiege(Long siegeId) {
        return siegeEventRepository
                .findById(siegeId)
                .orElseThrow(() -> new CustomException(ErrorCode.SIEGE_NOT_FOUND));
    }

    private void consumeScoutUnit(Long userId) {
        UnitType scout =
                unitTypeRepository
                        .findByName(SCOUT_UNIT_NAME)
                        .orElseThrow(() -> new CustomException(ErrorCode.UNIT_TYPE_NOT_FOUND));
        if (nullSafe(unitInstanceRepository.sumReadyIdleQuantity(userId, scout.getId(), 1)) < 1) {
            throw new CustomException(ErrorCode.SCOUT_UNIT_REQUIRED);
        }
        for (UnitInstance stack :
                unitInstanceRepository.findReadyIdleByUserIdAndUnitTypeIdAndLevel(
                        userId, scout.getId(), 1)) {
            if (stack.getQuantity() <= 0) {
                continue;
            }
            stack.subtractQuantity(1);
            if (stack.getQuantity() == 0) {
                unitInstanceRepository.delete(stack);
            }
            return;
        }
    }

    private List<SiegeTargetResponse.ZoneHp> buildZoneHps(
            List<BuildingInstance> buildings, BuildingLevelSpecResolver resolver) {
        List<SiegeTargetResponse.ZoneHp> zones = new ArrayList<>();
        for (int zone = 1; zone <= 3; zone++) {
            int currentZone = zone;
            List<BuildingInstance> inZone =
                    buildings.stream()
                            .filter(building -> building.getZone() == currentZone)
                            .toList();
            zones.add(
                    new SiegeTargetResponse.ZoneHp(
                            currentZone,
                            inZone.stream().mapToInt(BuildingInstance::getHp).sum(),
                            inZone.stream().mapToInt(resolver::maxHp).sum(),
                            inZone.size()));
        }
        return zones;
    }

    private SiegeTargetResponse.TargetBuilding toTargetBuilding(
            BuildingInstance building, BuildingLevelSpecResolver resolver, LocalDateTime now) {
        return new SiegeTargetResponse.TargetBuilding(
                building.getId(),
                building.getBuildingType().getName(),
                building.getBuildingType().getDisplayName(),
                building.getZone(),
                building.getHp(),
                resolver.maxHp(building),
                building.getPosX(),
                building.getPosY(),
                building.getBuildingType().getWidth(),
                building.getBuildingType().getHeight(),
                building.isUnderConstruction(now));
    }

    private SiegeEventListResponse.SiegeDto toSiegeDto(SiegeEvent siege) {
        BuildingInstance target = siege.getTargetBuilding();
        TerritoryCombatContext territory = findTerritory(siege.getTargetTerritoryId());
        return new SiegeEventListResponse.SiegeDto(
                siege.getId(),
                siege.getStatus().name(),
                new SiegeEventListResponse.UserDto(
                        siege.getAttackerId(), nickname(siege.getAttackerId())),
                new SiegeEventListResponse.UserDto(
                        siege.getDefenderId(), nickname(siege.getDefenderId())),
                new SiegeEventListResponse.TerritoryDto(
                        territory.territoryId(), territory.coordX(), territory.coordY()),
                siege.getAttackZone(),
                target == null
                        ? null
                        : new SiegeEventListResponse.TargetBuildingDto(
                                target.getId(),
                                target.getBuildingType().getName(),
                                target.getBuildingType().getDisplayName()),
                siege.getSiegeStartAt(),
                siege.getResolveAt());
    }

    private String nickname(Long userId) {
        return userSnapshotRepository
                .findById(userId)
                .map(CombatUserSnapshot::getNickname)
                .orElse(null);
    }

    private String resolveResult(SiegeEvent siege, String role) {
        return siegeResultRepository
                .findBySiegeId(siege.getId())
                .map(
                        result -> {
                            boolean attackerWin = Boolean.TRUE.equals(result.getIsAttackerWin());
                            return ("ATTACKER".equals(role) == attackerWin) ? "WIN" : "LOSE";
                        })
                .orElse("PENDING");
    }

    private boolean isResultFiltered(String result, String filter) {
        return filter != null
                && !"ALL".equalsIgnoreCase(filter)
                && !filter.equalsIgnoreCase(result);
    }

    private SiegeEvent.SiegeStatus parseSiegeStatus(String statusParam) {
        try {
            return SiegeEvent.SiegeStatus.valueOf(statusParam.toUpperCase());
        } catch (Exception ignored) {
            return SiegeEvent.SiegeStatus.PENDING;
        }
    }

    private int nullSafe(Integer value) {
        return value != null ? value : 0;
    }
}
