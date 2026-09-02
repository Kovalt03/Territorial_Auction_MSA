package com.territorial.combat.domain.military.service;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.combat.domain.building.entity.BuildingInstance;
import com.territorial.combat.domain.building.entity.CombatUserSnapshot;
import com.territorial.combat.domain.building.entity.GlobalVault;
import com.territorial.combat.domain.building.repository.BuildingInstanceRepository;
import com.territorial.combat.domain.building.repository.CombatUserSnapshotRepository;
import com.territorial.combat.domain.building.repository.GlobalVaultRepository;
import com.territorial.combat.domain.military.MilitaryPolicy;
import com.territorial.combat.domain.military.dto.DeclareSiegeRequest;
import com.territorial.combat.domain.military.dto.DeclareSiegeResponse;
import com.territorial.combat.domain.military.entity.AttackToken;
import com.territorial.combat.domain.military.entity.SiegeEvent;
import com.territorial.combat.domain.military.entity.SiegeForce;
import com.territorial.combat.domain.military.entity.SiegeResult;
import com.territorial.combat.domain.military.entity.SiegeStructure;
import com.territorial.combat.domain.military.entity.SiegeStructureType;
import com.territorial.combat.domain.military.entity.UnitInstance;
import com.territorial.combat.domain.military.entity.UnitType;
import com.territorial.combat.domain.military.port.SiegeTerritoryPort;
import com.territorial.combat.domain.military.port.SiegeTerritoryPort.TerritoryCombatContext;
import com.territorial.combat.domain.military.repository.AttackTokenRepository;
import com.territorial.combat.domain.military.repository.SiegeEventRepository;
import com.territorial.combat.domain.military.repository.SiegeForceRepository;
import com.territorial.combat.domain.military.repository.SiegeResultRepository;
import com.territorial.combat.domain.military.repository.SiegeStructureRepository;
import com.territorial.combat.domain.military.repository.UnitInstanceRepository;
import com.territorial.combat.domain.military.repository.UnitTypeRepository;
import com.territorial.combat.event.CombatOutboxService;
import com.territorial.combat.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@ConditionalOnBean(SiegeTerritoryPort.class)
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SiegeCommandService {

    public static final String SIEGE_DECLARED = "combat.siege.declared";

    private final SiegeTerritoryPort territoryPort;
    private final CombatUserSnapshotRepository userSnapshotRepository;
    private final SiegeEventRepository siegeEventRepository;
    private final SiegeResultRepository siegeResultRepository;
    private final SiegeForceRepository siegeForceRepository;
    private final SiegeStructureRepository siegeStructureRepository;
    private final AttackTokenRepository attackTokenRepository;
    private final UnitInstanceRepository unitInstanceRepository;
    private final UnitTypeRepository unitTypeRepository;
    private final BuildingInstanceRepository buildingInstanceRepository;
    private final GlobalVaultRepository globalVaultRepository;
    private final CombatOutboxService outboxService;

    @Transactional
    public DeclareSiegeResponse declareSiege(Long userId, DeclareSiegeRequest request) {
        TerritoryCombatContext target = findTerritoryOrThrow(request.targetTerritoryId());
        validateTarget(target, userId);
        validateAttackCooldown(target.territoryId(), userId);
        validateZoneCleared(target.territoryId(), userId, request.attackZone());
        validateAttackerForces(userId, request.forces());
        validateSiegeStructures(request.structures(), target);
        validateForcesWithinStagingCapacity(request.forces(), request.structures());

        AttackToken token = findAttackTokenOrThrow(userId);
        BuildingInstance targetBuilding = resolveTargetBuilding(request.targetBuildingId());
        validateTargetBuilding(target, request.attackZone(), targetBuilding);
        consumeAttackToken(token, targetBuilding);

        CombatUserSnapshot attacker = findUserSnapshotOrThrow(userId);
        CombatUserSnapshot defender = findUserSnapshotOrThrow(target.ownerId());
        LocalDateTime now = LocalDateTime.now();
        SiegeEvent siege =
                siegeEventRepository.save(
                        SiegeEvent.builder()
                                .attackerId(userId)
                                .defenderId(target.ownerId())
                                .targetTerritoryId(target.territoryId())
                                .targetBuilding(targetBuilding)
                                .attackZone(request.attackZone())
                                .siegeStartAt(now)
                                .resolveAt(now.plusMinutes(MilitaryPolicy.SIEGE_COUNTDOWN_MINUTES))
                                .build());
        commitAttackerForces(siege, userId, request.forces());
        chargeVaultForStructures(userId, request.structures());
        saveSiegeStructures(siege, request.structures());

        int remaining = targetBuilding == null ? token.getNormalCount() : token.getPrecisionCount();
        outboxService.append(
                "SIEGE",
                siege.getId(),
                SIEGE_DECLARED,
                new SiegeDeclaredEvent(
                        siege.getId(),
                        target.territoryId(),
                        target.coordX(),
                        target.coordY(),
                        request.attackZone(),
                        userId,
                        attacker.getNickname(),
                        target.ownerId(),
                        defender.getNickname(),
                        siege.getResolveAt()));
        log.info(
                "공성전 선언. siegeId={}, attackerId={}, targetTerritoryId={}",
                siege.getId(),
                userId,
                target.territoryId());
        return new DeclareSiegeResponse(siege.getId(), siege.getResolveAt(), remaining);
    }

    private TerritoryCombatContext findTerritoryOrThrow(Long territoryId) {
        return territoryPort
                .findById(territoryId)
                .orElseThrow(() -> new CustomException(ErrorCode.TERRITORY_NOT_FOUND));
    }

    private CombatUserSnapshot findUserSnapshotOrThrow(Long userId) {
        return userSnapshotRepository
                .findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    private void validateTarget(TerritoryCombatContext target, Long attackerId) {
        if (attackerId.equals(target.ownerId())) {
            throw new CustomException(ErrorCode.CANNOT_ATTACK_OWN_TERRITORY);
        }
        if (!target.occupied() || target.ownerId() == null) {
            throw new CustomException(ErrorCode.TERRITORY_NOT_OCCUPIED);
        }
        if (target.protectedUntil() != null
                && LocalDateTime.now().isBefore(target.protectedUntil())) {
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
                        .filter(result -> !result.getIsAttackerWin())
                        .map(
                                result ->
                                        last.getResolveAt()
                                                .plusHours(cooldownHours(result))
                                                .isAfter(LocalDateTime.now()))
                        .orElse(false);
        if (inCooldown) {
            throw new CustomException(ErrorCode.ATTACK_COOLDOWN);
        }
    }

    private int cooldownHours(SiegeResult result) {
        return result.getAppliedCooldownHours() != null
                ? result.getAppliedCooldownHours()
                : MilitaryPolicy.ATTACK_COOLDOWN_HOURS;
    }

    private void validateZoneCleared(Long territoryId, Long attackerId, int attackZone) {
        if (attackZone >= MilitaryPolicy.OUTERMOST_ZONE) {
            return;
        }
        boolean cleared =
                siegeEventRepository
                        .findRecentByTerritoryAndAttacker(
                                territoryId, attackerId, SiegeEvent.SiegeStatus.RESOLVED)
                        .stream()
                        .filter(event -> event.getAttackZone() == attackZone + 1)
                        .anyMatch(
                                event ->
                                        siegeResultRepository
                                                .findBySiegeId(event.getId())
                                                .map(SiegeResult::getIsAttackerWin)
                                                .orElse(false));
        if (!cleared) {
            throw new CustomException(ErrorCode.ZONE_NOT_CLEARED);
        }
    }

    private void validateAttackerForces(Long userId, List<DeclareSiegeRequest.ForceEntry> forces) {
        for (DeclareSiegeRequest.ForceEntry force : forces) {
            int available =
                    nullSafe(
                            unitInstanceRepository.sumReadyIdleQuantity(
                                    userId, force.unitTypeId(), levelOf(force.level())));
            if (available < force.quantity()) {
                throw new CustomException(ErrorCode.INSUFFICIENT_UNITS);
            }
        }
    }

    private void validateSiegeStructures(
            List<DeclareSiegeRequest.StructureEntry> structures, TerritoryCombatContext target) {
        if (structures.size() > MilitaryPolicy.SIEGE_STRUCTURE_MAX) {
            throw new CustomException(ErrorCode.SIEGE_STRUCTURE_LIMIT_EXCEEDED);
        }
        if (structures.stream().noneMatch(entry -> entry.type() == SiegeStructureType.STAGING)) {
            throw new CustomException(ErrorCode.SIEGE_STAGING_REQUIRED);
        }
        Set<String> positions = new HashSet<>();
        for (DeclareSiegeRequest.StructureEntry entry : structures) {
            validateAdjacentTile(entry, target);
            if (!positions.add(entry.coordX() + ":" + entry.coordY())) {
                throw new CustomException(ErrorCode.SIEGE_STRUCTURE_PLACEMENT_INVALID);
            }
        }
    }

    private void validateAdjacentTile(
            DeclareSiegeRequest.StructureEntry entry, TerritoryCombatContext target) {
        int dx = Math.abs(entry.coordX() - target.coordX());
        int dy = Math.abs(entry.coordY() - target.coordY());
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

    private void validateForcesWithinStagingCapacity(
            List<DeclareSiegeRequest.ForceEntry> forces,
            List<DeclareSiegeRequest.StructureEntry> structures) {
        long stagingCount =
                structures.stream()
                        .filter(entry -> entry.type() == SiegeStructureType.STAGING)
                        .count();
        int capacity = (int) stagingCount * MilitaryPolicy.STAGING_CAPACITY_PER;
        int totalForces = forces.stream().mapToInt(DeclareSiegeRequest.ForceEntry::quantity).sum();
        if (totalForces > capacity) {
            throw new CustomException(ErrorCode.SIEGE_FORCE_EXCEEDS_CAPACITY);
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

    private void validateTargetBuilding(
            TerritoryCombatContext target, Integer attackZone, BuildingInstance building) {
        if (building == null) {
            return;
        }
        if (!target.territoryId().equals(building.getTerritoryId())
                || !attackZone.equals(building.getZone())
                || building.isDestroyed()) {
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

    private void commitAttackerForces(
            SiegeEvent siege, Long userId, List<DeclareSiegeRequest.ForceEntry> forces) {
        for (DeclareSiegeRequest.ForceEntry force : forces) {
            UnitType unitType =
                    unitTypeRepository
                            .findById(force.unitTypeId())
                            .orElseThrow(() -> new CustomException(ErrorCode.UNIT_TYPE_NOT_FOUND));
            int level = levelOf(force.level());
            deductReadyIdle(userId, force.unitTypeId(), level, force.quantity());
            siegeForceRepository.save(
                    SiegeForce.builder()
                            .siege(siege)
                            .unitType(unitType)
                            .quantity(force.quantity())
                            .level(level)
                            .build());
        }
    }

    private void deductReadyIdle(Long userId, Long unitTypeId, int level, int quantity) {
        int remaining = quantity;
        for (UnitInstance stack :
                unitInstanceRepository.findReadyIdleByUserIdAndUnitTypeIdAndLevel(
                        userId, unitTypeId, level)) {
            if (remaining <= 0) {
                break;
            }
            int take = Math.min(stack.getQuantity(), remaining);
            stack.subtractQuantity(take);
            remaining -= take;
            if (stack.getQuantity() <= 0) {
                unitInstanceRepository.delete(stack);
            }
        }
    }

    private void chargeVaultForStructures(
            Long userId, List<DeclareSiegeRequest.StructureEntry> structures) {
        int cost =
                structures.stream()
                        .mapToInt(entry -> MilitaryPolicy.structureCostGp(entry.type()))
                        .sum();
        GlobalVault vault =
                globalVaultRepository
                        .findByIdWithLock(userId)
                        .orElseThrow(() -> new CustomException(ErrorCode.INSUFFICIENT_GP));
        if (vault.getStoredGp() < cost) {
            throw new CustomException(ErrorCode.INSUFFICIENT_GP);
        }
        vault.withdrawGp(cost);
    }

    private void saveSiegeStructures(
            SiegeEvent siege, List<DeclareSiegeRequest.StructureEntry> structures) {
        structures.forEach(
                entry ->
                        siegeStructureRepository.save(
                                SiegeStructure.builder()
                                        .siege(siege)
                                        .type(entry.type())
                                        .coordX(entry.coordX())
                                        .coordY(entry.coordY())
                                        .build()));
    }

    private int levelOf(Integer level) {
        return level != null ? level : 1;
    }

    private int nullSafe(Integer value) {
        return value != null ? value : 0;
    }

    public record SiegeDeclaredEvent(
            Long siegeId,
            Long territoryId,
            int coordX,
            int coordY,
            int attackZone,
            Long attackerId,
            String attackerNickname,
            Long defenderId,
            String defenderNickname,
            LocalDateTime resolveAt) {}
}
