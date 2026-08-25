package com.territorial.auction.domain.military.service;

import com.territorial.auction.domain.building.entity.GlobalVault;
import com.territorial.auction.domain.building.repository.BuildingInstanceRepository;
import com.territorial.auction.domain.building.repository.GlobalVaultRepository;
import com.territorial.auction.domain.military.ResearchPolicy;
import com.territorial.auction.domain.military.UnitPolicy;
import com.territorial.auction.domain.military.dto.ResearchStatusResponse;
import com.territorial.auction.domain.military.dto.ResearchStatusResponse.UnitResearchDto;
import com.territorial.auction.domain.military.dto.StartResearchResponse;
import com.territorial.auction.domain.military.entity.UnitResearch;
import com.territorial.auction.domain.military.entity.UnitType;
import com.territorial.auction.domain.military.entity.UnitTypeLevelSpec;
import com.territorial.auction.domain.military.repository.UnitResearchRepository;
import com.territorial.auction.domain.military.repository.UnitTypeLevelSpecRepository;
import com.territorial.auction.domain.military.repository.UnitTypeRepository;
import com.territorial.auction.domain.user.entity.User;
import com.territorial.auction.domain.user.repository.UserRepository;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ResearchService {

    private final UnitResearchRepository unitResearchRepository;
    private final UnitTypeRepository unitTypeRepository;
    private final UnitTypeLevelSpecRepository unitTypeLevelSpecRepository;
    private final BuildingInstanceRepository buildingInstanceRepository;
    private final GlobalVaultRepository globalVaultRepository;
    private final UserRepository userRepository;

    public ResearchStatusResponse getResearch(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        int labLevel = buildingInstanceRepository.findMaxResearchLabLevelByUserId(userId).orElse(0);

        Map<Long, UnitResearch> byType =
                unitResearchRepository.findByUserId(userId).stream()
                        .peek(r -> r.applyCompletionIfDue(now)) // 완료 정산(관리 엔티티라 자동 반영)
                        .collect(
                                Collectors.toMap(
                                        r -> r.getUnitType().getId(), Function.identity()));

        List<UnitResearchDto> dtos =
                unitTypeRepository.findAll().stream()
                        .map(type -> toDto(type, byType.get(type.getId())))
                        .toList();
        return new ResearchStatusResponse(labLevel, dtos);
    }

    public StartResearchResponse startResearch(Long userId, Long unitTypeId) {
        UnitType unitType = findUnitTypeOrThrow(unitTypeId);
        LocalDateTime now = LocalDateTime.now();

        // 계정 단위 금고 락을 연구 상태 조회보다 먼저 잡아 동시 연구 시작을 직렬화한다.
        GlobalVault vault = findVaultWithLock(userId);

        // 연구는 계정당 한 번에 하나만 — 다른 유닛이 연구 중이면 새 연구를 시작할 수 없다.
        validateNoResearchInProgress(userId, now);

        UnitResearch research = findOrCreate(userId, unitType);
        research.applyCompletionIfDue(now);
        if (research.isResearching(now)) {
            throw new CustomException(ErrorCode.RESEARCH_IN_PROGRESS);
        }

        int targetLevel = research.getResearchedLevel() + 1;
        validateTargetLevel(unitTypeId, targetLevel);
        validateLabLevel(userId, targetLevel);

        chargeVault(vault, ResearchPolicy.costGp(targetLevel));
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

    private void validateTargetLevel(Long unitTypeId, int targetLevel) {
        if (targetLevel > UnitPolicy.MAX_LEVEL) {
            throw new CustomException(ErrorCode.RESEARCH_MAX_REACHED);
        }
        unitTypeLevelSpecRepository
                .findByUnitType_IdAndLevel(unitTypeId, targetLevel)
                .orElseThrow(() -> new CustomException(ErrorCode.RESEARCH_SPEC_NOT_FOUND));
    }

    private void validateLabLevel(Long userId, int targetLevel) {
        int labLevel = buildingInstanceRepository.findMaxResearchLabLevelByUserId(userId).orElse(0);
        if (labLevel < ResearchPolicy.requiredLabLevel(targetLevel)) {
            throw new CustomException(ErrorCode.RESEARCH_LAB_LEVEL_INSUFFICIENT);
        }
    }

    private GlobalVault findVaultWithLock(Long userId) {
        return globalVaultRepository
                .findByIdWithLock(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.INSUFFICIENT_GP));
    }

    private void chargeVault(GlobalVault vault, int cost) {
        if (vault.getStoredGp() < cost) {
            throw new CustomException(ErrorCode.INSUFFICIENT_GP);
        }
        vault.withdrawGp(cost);
    }

    // 계정 전체에서 진행 중인 연구가 하나라도 있으면 거부한다(한 번에 하나).
    private void validateNoResearchInProgress(Long userId, LocalDateTime now) {
        boolean anyInProgress =
                unitResearchRepository.findByUserId(userId).stream()
                        .peek(r -> r.applyCompletionIfDue(now))
                        .anyMatch(r -> r.isResearching(now));
        if (anyInProgress) {
            throw new CustomException(ErrorCode.RESEARCH_IN_PROGRESS);
        }
    }

    private UnitResearch findOrCreate(Long userId, UnitType unitType) {
        return unitResearchRepository
                .findByUserIdAndUnitTypeId(userId, unitType.getId())
                .orElseGet(
                        () -> {
                            User user = userRepository.getReferenceById(userId);
                            return unitResearchRepository.save(
                                    UnitResearch.builder()
                                            .user(user)
                                            .unitType(unitType)
                                            .researchedLevel(1)
                                            .build());
                        });
    }

    private UnitResearchDto toDto(UnitType type, UnitResearch research) {
        int researchedLevel = research != null ? research.getResearchedLevel() : 1;
        int maxLevel = maxAvailableLevel(type.getId());
        Integer nextCost =
                (researchedLevel < maxLevel && researchedLevel < UnitPolicy.MAX_LEVEL)
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
