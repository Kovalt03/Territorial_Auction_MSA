package com.territorial.combat.domain.military.service;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.combat.domain.building.BuildingLevelSpecResolver;
import com.territorial.combat.domain.building.StoragePolicy;
import com.territorial.combat.domain.building.entity.BuildingInstance;
import com.territorial.combat.domain.building.entity.CombatUserSnapshot;
import com.territorial.combat.domain.building.entity.GlobalVault;
import com.territorial.combat.domain.building.entity.HomeIsland;
import com.territorial.combat.domain.building.repository.*;
import com.territorial.combat.domain.military.MilitaryPolicy;
import com.territorial.combat.domain.military.entity.*;
import com.territorial.combat.domain.military.port.SiegeTerritoryPort;
import com.territorial.combat.domain.military.port.SiegeTerritoryPort.TerritoryCombatContext;
import com.territorial.combat.domain.military.repository.*;
import com.territorial.combat.event.CombatOutboxService;
import com.territorial.combat.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
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
public class SiegeResolutionService {

    public static final String SIEGE_RESOLVED = "combat.siege.resolved";
    public static final String TERRITORY_TAKEOVER_REQUESTED = "combat.territory.takeover-requested";
    public static final String SIEGE_VICTORY = "combat.siege.victory";

    private final SiegeEventRepository siegeEventRepository;
    private final SiegeResultRepository siegeResultRepository;
    private final SiegeForceRepository siegeForceRepository;
    private final SiegeStructureRepository siegeStructureRepository;
    private final UnitInstanceRepository unitInstanceRepository;
    private final UnitTypeLevelSpecRepository unitTypeLevelSpecRepository;
    private final HomeIslandRepository homeIslandRepository;
    private final BuildingInstanceRepository buildingInstanceRepository;
    private final BuildingLevelSpecRepository buildingLevelSpecRepository;
    private final GlobalVaultRepository globalVaultRepository;
    private final CombatUserSnapshotRepository userSnapshotRepository;
    private final SiegeTerritoryPort territoryPort;
    private final UnitRetreatService unitRetreatService;
    private final CombatOutboxService outboxService;

    @Transactional
    public void resolveOneSiege(SiegeEvent pending) {
        SiegeEvent event =
                siegeEventRepository
                        .findById(pending.getId())
                        .orElseThrow(() -> new CustomException(ErrorCode.SIEGE_NOT_FOUND));
        TerritoryCombatContext territory =
                territoryPort
                        .findById(event.getTargetTerritoryId())
                        .orElseThrow(() -> new CustomException(ErrorCode.TERRITORY_NOT_FOUND));
        CombatUserSnapshot attacker = findUser(event.getAttackerId());
        CombatUserSnapshot defender = findUser(event.getDefenderId());
        List<SiegeForce> attackerForces = siegeForceRepository.findBySiegeId(event.getId());
        List<SiegeStructure> structures = siegeStructureRepository.findBySiegeId(event.getId());
        List<UnitInstance> defenderUnits =
                unitInstanceRepository.findDefendersInZone(
                        event.getDefenderId(), event.getTargetTerritoryId(), event.getAttackZone());

        int attackerAtk = applyTowerBonus(calculateForceAtk(attackerForces), structures);
        boolean attackerWin = attackerAtk > calculateDef(defenderUnits, event);
        int totalAttackerUnits = attackerForces.stream().mapToInt(SiegeForce::getQuantity).sum();
        int totalDefenderUnits = sumQuantity(defenderUnits);
        int attackerLost = calculateAttackerLost(totalAttackerUnits, attackerWin);
        int defenderLost =
                attackerWin
                        ? (int) Math.ceil(totalDefenderUnits * MilitaryPolicy.DEFENDER_LOSS_RATE)
                        : 0;
        applyAttackerForceLoss(attackerForces, attackerLost);
        if (attackerWin) {
            deductUnits(defenderUnits, defenderLost);
        }
        int buildingDamage =
                attackerForces.stream()
                        .mapToInt(
                                force ->
                                        force.getUnitType().getBuildingDamage()
                                                * force.getQuantity())
                        .sum();
        ResultEffect effect = applyResultEffect(event, attackerWin, buildingDamage);

        returnAttackerSurvivors(event.getAttackerId(), attackerForces);
        siegeForceRepository.deleteAll(attackerForces);
        siegeStructureRepository.deleteAll(structures);
        int cooldownHours = supplyReducedCooldownHours(structures);
        siegeResultRepository.save(
                SiegeResult.builder()
                        .siege(event)
                        .isAttackerWin(attackerWin)
                        .attackerUnitsLost(attackerLost)
                        .defenderUnitsLost(defenderLost)
                        .lootedGp(effect.lootedGp())
                        .resultType(effect.resultType())
                        .appliedCooldownHours(cooldownHours)
                        .build());
        event.resolve();

        outboxService.append(
                "SIEGE",
                event.getId(),
                SIEGE_RESOLVED,
                new SiegeResolvedEvent(
                        event.getId(),
                        event.getTargetTerritoryId(),
                        territory.coordX(),
                        territory.coordY(),
                        event.getAttackZone(),
                        event.getAttackerId(),
                        attacker.getNickname(),
                        event.getDefenderId(),
                        defender.getNickname(),
                        attackerWin,
                        effect.resultType() != null ? effect.resultType().name() : null,
                        attackerLost,
                        defenderLost,
                        effect.lootedGp(),
                        event.getResolveAt()));
        if (attackerWin) {
            outboxService.append(
                    "USER",
                    event.getAttackerId(),
                    SIEGE_VICTORY,
                    new SiegeVictoryEvent(event.getId(), event.getAttackerId()));
        }
        log.info(
                "공성전 처리 완료. siegeId={}, territoryId={}, attackerWin={}, resultType={}",
                event.getId(),
                event.getTargetTerritoryId(),
                attackerWin,
                effect.resultType());
    }

    private CombatUserSnapshot findUser(Long userId) {
        return userSnapshotRepository
                .findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    private int calculateForceAtk(List<SiegeForce> forces) {
        return forces.stream()
                .mapToInt(
                        force ->
                                resolveAtk(force.getUnitType(), force.getLevel())
                                        * force.getQuantity())
                .sum();
    }

    private int resolveAtk(UnitType type, Integer level) {
        int resolvedLevel = level != null ? level : 1;
        if (resolvedLevel <= 1) {
            return type.getAttackPower();
        }
        return unitTypeLevelSpecRepository
                .findByUnitType_IdAndLevel(type.getId(), resolvedLevel)
                .map(UnitTypeLevelSpec::getAttackPower)
                .orElse(type.getAttackPower());
    }

    private int resolveDef(UnitType type, Integer level) {
        int resolvedLevel = level != null ? level : 1;
        if (resolvedLevel <= 1) {
            return type.getDefensePower();
        }
        return unitTypeLevelSpecRepository
                .findByUnitType_IdAndLevel(type.getId(), resolvedLevel)
                .map(UnitTypeLevelSpec::getDefensePower)
                .orElse(type.getDefensePower());
    }

    private int applyTowerBonus(int baseAttack, List<SiegeStructure> structures) {
        long towers =
                structures.stream()
                        .filter(structure -> structure.getType() == SiegeStructureType.TOWER)
                        .count();
        int effective = (int) Math.min(towers, MilitaryPolicy.SIEGE_TOWER_MAX_EFFECTIVE);
        return baseAttack
                + baseAttack * effective * MilitaryPolicy.SIEGE_TOWER_ATK_BONUS_PERCENT / 100;
    }

    private int supplyReducedCooldownHours(List<SiegeStructure> structures) {
        long supplies =
                structures.stream()
                        .filter(structure -> structure.getType() == SiegeStructureType.SUPPLY)
                        .count();
        return Math.max(
                0,
                MilitaryPolicy.ATTACK_COOLDOWN_HOURS
                        - (int) supplies * MilitaryPolicy.SUPPLY_COOLDOWN_REDUCTION_HOURS);
    }

    private int calculateDef(List<UnitInstance> defenderUnits, SiegeEvent event) {
        int unitDefense =
                defenderUnits.stream()
                        .mapToInt(
                                unit ->
                                        resolveDef(unit.getUnitType(), unit.getLevel())
                                                * unit.getQuantity())
                        .sum();
        List<BuildingInstance> buildings =
                buildingInstanceRepository.findActiveByTerritoryIdAndZone(
                        event.getTargetTerritoryId(), event.getAttackZone());
        BuildingLevelSpecResolver resolver =
                BuildingLevelSpecResolver.of(buildings, buildingLevelSpecRepository);
        LocalDateTime now = LocalDateTime.now();
        int buildingDefense =
                buildings.stream()
                        .filter(building -> !building.isUnderConstruction(now))
                        .mapToInt(resolver::defense)
                        .sum();
        return unitDefense + buildingDefense;
    }

    private void applyAttackerForceLoss(List<SiegeForce> forces, int totalLost) {
        int remaining = totalLost;
        for (SiegeForce force : forces) {
            if (remaining <= 0) {
                break;
            }
            int deducted = Math.min(force.getQuantity(), remaining);
            force.subtractQuantity(deducted);
            remaining -= deducted;
        }
    }

    private void returnAttackerSurvivors(Long attackerId, List<SiegeForce> forces) {
        homeIslandRepository
                .findByUserId(attackerId)
                .ifPresent(
                        island ->
                                forces.stream()
                                        .filter(force -> force.getQuantity() > 0)
                                        .forEach(
                                                force ->
                                                        addIslandReadyIdle(
                                                                attackerId, island, force)));
    }

    private void addIslandReadyIdle(Long userId, HomeIsland island, SiegeForce force) {
        unitInstanceRepository
                .findReadyIdleAtIsland(
                        userId, force.getUnitType().getId(), force.getLevel(), island.getId())
                .ifPresentOrElse(
                        idle -> idle.addQuantity(force.getQuantity()),
                        () ->
                                unitInstanceRepository.save(
                                        UnitInstance.builder()
                                                .userId(userId)
                                                .unitType(force.getUnitType())
                                                .quantity(force.getQuantity())
                                                .level(force.getLevel())
                                                .homeIsland(island)
                                                .build()));
    }

    private int calculateAttackerLost(int totalUnits, boolean attackerWin) {
        double rate =
                attackerWin
                        ? MilitaryPolicy.ATTACKER_LOSS_RATE
                        : MilitaryPolicy.ATTACKER_FAIL_LOSS_RATE;
        return (int) Math.ceil(totalUnits * rate);
    }

    private void deductUnits(List<UnitInstance> units, int totalLost) {
        int remaining = totalLost;
        for (UnitInstance unit : units) {
            if (remaining <= 0) {
                break;
            }
            int deducted = Math.min(unit.getQuantity(), remaining);
            unit.subtractQuantity(deducted);
            remaining -= deducted;
            if (unit.getQuantity() <= 0) {
                unitInstanceRepository.delete(unit);
            }
        }
    }

    private ResultEffect applyResultEffect(
            SiegeEvent event, boolean attackerWin, int buildingDamage) {
        if (!attackerWin) {
            return new ResultEffect(null, 0);
        }
        return switch (event.getAttackZone()) {
            case 3 -> new ResultEffect(SiegeResult.ResultType.LOOT, applyLoot(event));
            case 2 -> {
                applyDebuff(event);
                yield new ResultEffect(SiegeResult.ResultType.DEBUFF, 0);
            }
            case 1 -> {
                applyCastleDamage(event, buildingDamage);
                yield new ResultEffect(SiegeResult.ResultType.AUCTION, 0);
            }
            default -> new ResultEffect(null, 0);
        };
    }

    private int applyLoot(SiegeEvent event) {
        List<BuildingInstance> storages = resolveLootStorages(event);
        int totalLooted = 0;
        for (BuildingInstance storage : storages) {
            totalLooted +=
                    storage.loot(
                            (int) Math.floor(storage.getStoredGp() * MilitaryPolicy.LOOT_RATE));
        }
        if (totalLooted > 0) {
            creditAttackerVault(event.getAttackerId(), totalLooted);
        }
        return totalLooted;
    }

    private List<BuildingInstance> resolveLootStorages(SiegeEvent event) {
        BuildingInstance target = event.getTargetBuilding();
        if (target != null && "STORAGE".equals(target.getBuildingType().getName())) {
            return List.of(target);
        }
        return buildingInstanceRepository
                .findActiveByTerritoryIdAndZone(event.getTargetTerritoryId(), 3)
                .stream()
                .filter(building -> "STORAGE".equals(building.getBuildingType().getName()))
                .toList();
    }

    private void creditAttackerVault(Long attackerId, int amount) {
        GlobalVault vault =
                globalVaultRepository
                        .findByIdWithLock(attackerId)
                        .orElseGet(
                                () ->
                                        globalVaultRepository.save(
                                                GlobalVault.builder().userId(attackerId).build()));
        vault.receiveGp(amount);
    }

    private void applyDebuff(SiegeEvent event) {
        BuildingInstance target = event.getTargetBuilding();
        List<BuildingInstance> buildings =
                target != null
                        ? List.of(target)
                        : buildingInstanceRepository.findActiveByTerritoryIdAndZone(
                                event.getTargetTerritoryId(), 2);
        LocalDateTime debuffUntil =
                LocalDateTime.now().plusHours(MilitaryPolicy.WORKSHOP_DEBUFF_HOURS);
        for (BuildingInstance building : buildings) {
            building.takeDamage(building.getBuildingType().getMaxHp() / 2);
            if (building.isDestroyed() && "WORKSHOP".equals(building.getBuildingType().getName())) {
                building.applyWorkshopDebuff(debuffUntil);
            }
            if (building.isDestroyed() && !"CASTLE".equals(building.getBuildingType().getName())) {
                unitRetreatService.retreatFromDestroyedBuilding(
                        event.getDefenderId(), building.getId());
            }
        }
    }

    private void applyCastleDamage(SiegeEvent event, int buildingDamage) {
        List<BuildingInstance> buildings =
                buildingInstanceRepository.findActiveByTerritoryIdAndZone(
                        event.getTargetTerritoryId(), 1);
        boolean castleDestroyed;
        if (event.getTargetBuilding() != null) {
            event.getTargetBuilding().takeDamage(buildingDamage);
            castleDestroyed =
                    event.getTargetBuilding().isDestroyed()
                            && "CASTLE"
                                    .equals(event.getTargetBuilding().getBuildingType().getName());
        } else {
            castleDestroyed = applyDamageEvenly(buildings, buildingDamage);
        }
        if (castleDestroyed) {
            requestTerritoryTakeover(event);
        } else {
            for (BuildingInstance building : buildings) {
                if (building.isDestroyed()
                        && !"CASTLE".equals(building.getBuildingType().getName())) {
                    unitRetreatService.retreatFromDestroyedBuilding(
                            event.getDefenderId(), building.getId());
                }
            }
        }
    }

    private boolean applyDamageEvenly(List<BuildingInstance> buildings, int damage) {
        if (buildings.isEmpty()) {
            return false;
        }
        int each = damage / buildings.size();
        boolean castleDestroyed = false;
        for (BuildingInstance building : buildings) {
            building.takeDamage(each);
            if (building.isDestroyed() && "CASTLE".equals(building.getBuildingType().getName())) {
                castleDestroyed = true;
            }
        }
        return castleDestroyed;
    }

    private void requestTerritoryTakeover(SiegeEvent event) {
        List<BuildingInstance> storages =
                buildingInstanceRepository.findStorageBuildingsByTerritoryIdWithLock(
                        event.getTargetTerritoryId());
        int totalGp = StoragePolicy.drainAllGp(storages);
        StoragePolicy.drainAllFood(storages);
        int recovered = (int) Math.floor(totalGp * StoragePolicy.TERRITORY_LOSS_TRANSFER_RATE);
        if (recovered > 0) {
            creditAttackerVault(event.getAttackerId(), recovered);
        }
        unitInstanceRepository.deleteAll(
                unitInstanceRepository.findByOwnerAndTerritoryAssociation(
                        event.getDefenderId(), event.getTargetTerritoryId()));
        outboxService.append(
                "TERRITORY",
                event.getTargetTerritoryId(),
                TERRITORY_TAKEOVER_REQUESTED,
                new TerritoryTakeoverRequestedEvent(
                        event.getId(),
                        event.getTargetTerritoryId(),
                        event.getAttackerId(),
                        event.getDefenderId(),
                        recovered));
    }

    private int sumQuantity(List<UnitInstance> units) {
        return units.stream().mapToInt(UnitInstance::getQuantity).sum();
    }

    private record ResultEffect(SiegeResult.ResultType resultType, int lootedGp) {}

    public record SiegeResolvedEvent(
            Long siegeId,
            Long territoryId,
            int coordX,
            int coordY,
            int attackZone,
            Long attackerId,
            String attackerNickname,
            Long defenderId,
            String defenderNickname,
            boolean isAttackerWin,
            String resultType,
            int attackerUnitsLost,
            int defenderUnitsLost,
            int lootedGp,
            LocalDateTime resolvedAt) {}

    public record TerritoryTakeoverRequestedEvent(
            Long siegeId, Long territoryId, Long newOwnerId, Long formerOwnerId, int recoveredGp) {}

    public record SiegeVictoryEvent(Long siegeId, Long attackerId) {}
}
