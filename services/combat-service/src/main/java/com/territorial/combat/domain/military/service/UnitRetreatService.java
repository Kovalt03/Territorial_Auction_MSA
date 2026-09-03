package com.territorial.combat.domain.military.service;

import com.territorial.combat.domain.building.entity.HomeIsland;
import com.territorial.combat.domain.building.repository.BuildingInstanceRepository;
import com.territorial.combat.domain.building.repository.HomeIslandRepository;
import com.territorial.combat.domain.military.MilitaryPolicy;
import com.territorial.combat.domain.military.entity.UnitInstance;
import com.territorial.combat.domain.military.repository.UnitInstanceRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UnitRetreatService {

    private final UnitInstanceRepository unitInstanceRepository;
    private final HomeIslandRepository homeIslandRepository;
    private final BuildingInstanceRepository buildingInstanceRepository;

    @Transactional
    public void retreatFromDestroyedBuilding(Long defenderId, Long buildingId) {
        retreatToIsland(defenderId, unitInstanceRepository.findByDeployedBuildingId(buildingId));
    }

    @Transactional
    public void retreatFromLostTerritory(Long formerOwnerId, Long territoryId) {
        retreatToIsland(
                formerOwnerId,
                unitInstanceRepository.findByOwnerAndTerritoryAssociation(
                        formerOwnerId, territoryId));
    }

    private void retreatToIsland(Long userId, List<UnitInstance> units) {
        if (units.isEmpty()) {
            return;
        }
        homeIslandRepository
                .findByUserId(userId)
                .ifPresentOrElse(
                        island -> retreatToExistingIsland(userId, island, units),
                        () -> unitInstanceRepository.deleteAll(units));
    }

    private void retreatToExistingIsland(Long userId, HomeIsland island, List<UnitInstance> units) {
        int free =
                Math.max(
                        0,
                        islandCapacity(island.getId())
                                - nullSafe(
                                        unitInstanceRepository.sumQuantityByHomeIslandId(
                                                island.getId())));
        for (UnitInstance unit : units) {
            boolean alreadyOnIsland =
                    unit.getHomeIsland() != null
                            && unit.getHomeIsland().getId().equals(island.getId());
            int accepted =
                    alreadyOnIsland ? unit.getQuantity() : Math.min(unit.getQuantity(), free);
            if (!alreadyOnIsland) {
                free -= accepted;
            }
            if (accepted > 0) {
                addIslandIdle(userId, island, unit, accepted);
            }
            unitInstanceRepository.delete(unit);
        }
        log.info(
                "유닛 홈 아일랜드 퇴각. userId={}, islandId={}, stacks={}",
                userId,
                island.getId(),
                units.size());
    }

    private void addIslandIdle(Long userId, HomeIsland island, UnitInstance source, int quantity) {
        unitInstanceRepository
                .findReadyIdleAtIsland(
                        userId, source.getUnitType().getId(), source.getLevel(), island.getId())
                .ifPresentOrElse(
                        idle -> idle.addQuantity(quantity),
                        () ->
                                unitInstanceRepository.save(
                                        UnitInstance.builder()
                                                .userId(userId)
                                                .unitType(source.getUnitType())
                                                .quantity(quantity)
                                                .level(source.getLevel())
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

    private int nullSafe(Integer value) {
        return value != null ? value : 0;
    }
}
