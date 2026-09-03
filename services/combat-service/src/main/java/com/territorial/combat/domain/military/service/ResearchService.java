package com.territorial.combat.domain.military.service;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.combat.domain.building.entity.GlobalVault;
import com.territorial.combat.domain.building.port.TerritoryContextPort;
import com.territorial.combat.domain.building.repository.BuildingInstanceRepository;
import com.territorial.combat.domain.building.repository.GlobalVaultRepository;
import com.territorial.combat.domain.military.ResearchPolicy;
import com.territorial.combat.domain.military.UnitPolicy;
import com.territorial.combat.domain.military.dto.ResearchStatusResponse;
import com.territorial.combat.domain.military.dto.ResearchStatusResponse.UnitResearchDto;
import com.territorial.combat.domain.military.dto.StartResearchResponse;
import com.territorial.combat.domain.military.entity.UnitResearch;
import com.territorial.combat.domain.military.entity.UnitType;
import com.territorial.combat.domain.military.entity.UnitTypeLevelSpec;
import com.territorial.combat.domain.military.repository.UnitResearchRepository;
import com.territorial.combat.domain.military.repository.UnitTypeLevelSpecRepository;
import com.territorial.combat.domain.military.repository.UnitTypeRepository;
import com.territorial.combat.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@ConditionalOnBean(TerritoryContextPort.class)
@RequiredArgsConstructor
@Transactional
public class ResearchService {

    private final UnitResearchRepository unitResearchRepository;
    private final UnitTypeRepository unitTypeRepository;
    private final UnitTypeLevelSpecRepository unitTypeLevelSpecRepository;
    private final BuildingInstanceRepository buildingInstanceRepository;
    private final GlobalVaultRepository globalVaultRepository;
    private final TerritoryContextPort territoryContextPort;

    public ResearchStatusResponse getResearch(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        int labLevel = findLabLevel(userId);
        Map<Long, UnitResearch> byType =
                unitResearchRepository.findByUserId(userId).stream()
                        .peek(research -> research.applyCompletionIfDue(now))
                        .collect(
                                Collectors.toMap(
                                        research -> research.getUnitType().getId(),
                                        Function.identity()));
        List<UnitResearchDto> units =
                unitTypeRepository.findAll().stream()
                        .map(type -> toDto(type, byType.get(type.getId())))
                        .toList();
        return new ResearchStatusResponse(labLevel, units);
    }

    public StartResearchResponse startResearch(Long userId, Long unitTypeId) {
        UnitType unitType = findUnitTypeOrThrow(unitTypeId);
        LocalDateTime now = LocalDateTime.now();
        GlobalVault vault = findVaultWithLock(userId);
        validateNoResearchInProgress(userId, now);

        UnitResearch research = findOrCreate(userId, unitType);
        research.applyCompletionIfDue(now);
        if (research.isResearching(now)) {
            throw new CustomException(ErrorCode.RESEARCH_IN_PROGRESS);
        }

        int targetLevel = research.getResearchedLevel() + 1;
        validateTargetLevel(unitTypeId, targetLevel);
        if (findLabLevel(userId) < ResearchPolicy.requiredLabLevel(targetLevel)) {
            throw new CustomException(ErrorCode.RESEARCH_LAB_LEVEL_INSUFFICIENT);
        }
        int cost = ResearchPolicy.costGp(targetLevel);
        if (vault.getStoredGp() < cost) {
            throw new CustomException(ErrorCode.INSUFFICIENT_GP);
        }
        vault.withdrawGp(cost);
        research.startResearch(
                targetLevel, now.plusMinutes(ResearchPolicy.durationMinutes(targetLevel)));
        log.info(
                "유닛 연구 시작. userId={}, unitTypeId={}, targetLevel={}",
                userId,
                unitTypeId,
                targetLevel);
        return new StartResearchResponse(
                unitTypeId, targetLevel, research.getResearchCompleteAt(), vault.getStoredGp());
    }

    private int findLabLevel(Long userId) {
        return buildingInstanceRepository
                .findMaxResearchLabLevelByUserId(
                        userId, territoryContextPort.findOwnedTerritoryIds(userId))
                .orElse(0);
    }

    private void validateTargetLevel(Long unitTypeId, int targetLevel) {
        if (targetLevel > UnitPolicy.MAX_LEVEL) {
            throw new CustomException(ErrorCode.RESEARCH_MAX_REACHED);
        }
        unitTypeLevelSpecRepository
                .findByUnitType_IdAndLevel(unitTypeId, targetLevel)
                .orElseThrow(() -> new CustomException(ErrorCode.RESEARCH_SPEC_NOT_FOUND));
    }

    private GlobalVault findVaultWithLock(Long userId) {
        return globalVaultRepository
                .findByIdWithLock(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.INSUFFICIENT_GP));
    }

    private void validateNoResearchInProgress(Long userId, LocalDateTime now) {
        boolean inProgress =
                unitResearchRepository.findByUserId(userId).stream()
                        .peek(research -> research.applyCompletionIfDue(now))
                        .anyMatch(research -> research.isResearching(now));
        if (inProgress) {
            throw new CustomException(ErrorCode.RESEARCH_IN_PROGRESS);
        }
    }

    private UnitResearch findOrCreate(Long userId, UnitType unitType) {
        return unitResearchRepository
                .findByUserIdAndUnitTypeId(userId, unitType.getId())
                .orElseGet(
                        () ->
                                unitResearchRepository.save(
                                        UnitResearch.builder()
                                                .userId(userId)
                                                .unitType(unitType)
                                                .researchedLevel(1)
                                                .build()));
    }

    private UnitResearchDto toDto(UnitType type, UnitResearch research) {
        int researchedLevel = research != null ? research.getResearchedLevel() : 1;
        int maxLevel = maxAvailableLevel(type.getId());
        Integer nextCost =
                researchedLevel < maxLevel && researchedLevel < UnitPolicy.MAX_LEVEL
                        ? ResearchPolicy.costGp(researchedLevel + 1)
                        : null;
        return new UnitResearchDto(
                type.getId(),
                type.getName(),
                type.getDisplayName(),
                type.getIcon(),
                type.getColorHex(),
                researchedLevel,
                maxLevel,
                research != null ? research.getPendingLevel() : null,
                research != null ? research.getResearchCompleteAt() : null,
                nextCost);
    }

    private int maxAvailableLevel(Long unitTypeId) {
        return unitTypeLevelSpecRepository.findAllByUnitType_Id(unitTypeId).stream()
                .mapToInt(UnitTypeLevelSpec::getLevel)
                .max()
                .orElse(1);
    }

    private UnitType findUnitTypeOrThrow(Long unitTypeId) {
        return unitTypeRepository
                .findById(unitTypeId)
                .orElseThrow(() -> new CustomException(ErrorCode.UNIT_TYPE_NOT_FOUND));
    }
}
