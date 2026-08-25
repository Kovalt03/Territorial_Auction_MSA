package com.territorial.auction.domain.admin.service;

import com.territorial.auction.domain.admin.dto.AdminUnitLevelSpecsRequest.UnitLevelValues;
import com.territorial.auction.domain.admin.dto.AdminUnitTypeResponse;
import com.territorial.auction.domain.admin.dto.AdminUpdateUnitTypeRequest;
import com.territorial.auction.domain.military.UnitPolicy;
import com.territorial.auction.domain.military.entity.UnitType;
import com.territorial.auction.domain.military.entity.UnitTypeLevelSpec;
import com.territorial.auction.domain.military.repository.UnitTypeLevelSpecRepository;
import com.territorial.auction.domain.military.repository.UnitTypeRepository;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUnitService {

    private final UnitTypeRepository unitTypeRepository;
    private final UnitTypeLevelSpecRepository unitTypeLevelSpecRepository;
    private final AdminAuditLogger adminAuditLogger;

    public List<AdminUnitTypeResponse> getUnitTypes() {
        return unitTypeRepository.findAll().stream().map(AdminUnitTypeResponse::from).toList();
    }

    @Transactional
    public AdminUnitTypeResponse update(
            Long adminUserId, Long unitTypeId, AdminUpdateUnitTypeRequest request) {
        UnitType unitType = findOrThrow(unitTypeId);
        unitType.update(
                blankToNull(request.displayName()),
                blankToNull(request.icon()),
                blankToNull(request.colorHex()),
                request.attackPower(),
                request.defensePower(),
                request.costGp(),
                request.foodCost(),
                request.buildingDamage(),
                request.level());

        adminAuditLogger.record(
                adminUserId,
                "UNIT_TYPE_UPDATE",
                "UNIT_TYPE",
                unitTypeId,
                Map.of("name", unitType.getName()));
        return AdminUnitTypeResponse.from(unitType);
    }

    // 훈련 레벨별 스펙 조회: {도달레벨: 값들}
    public Map<Integer, UnitLevelValues> getLevelSpecs(Long unitTypeId) {
        findOrThrow(unitTypeId);
        Map<Integer, UnitLevelValues> specs = new LinkedHashMap<>();
        unitTypeLevelSpecRepository.findAllByUnitType_Id(unitTypeId).stream()
                .sorted((a, b) -> a.getLevel() - b.getLevel())
                .forEach(s -> specs.put(s.getLevel(), UnitLevelValues.from(s)));
        return specs;
    }

    // 값이 모두 비어 있으면 그 레벨의 훈련을 없앤다.
    @Transactional
    public Map<Integer, UnitLevelValues> updateLevelSpecs(
            Long adminUserId, Long unitTypeId, Map<Integer, UnitLevelValues> specs) {
        UnitType unitType = findOrThrow(unitTypeId);
        specs.forEach((level, values) -> applyLevelSpec(unitType, level, values));

        adminAuditLogger.record(
                adminUserId,
                "UNIT_LEVEL_SPEC_UPDATE",
                "UNIT_TYPE",
                unitTypeId,
                Map.of("name", unitType.getName()));
        return getLevelSpecs(unitTypeId);
    }

    private void applyLevelSpec(UnitType unitType, Integer level, UnitLevelValues values) {
        if (level == null || level < 2 || level > UnitPolicy.MAX_LEVEL) {
            throw new CustomException(ErrorCode.INVALID_UNIT_LEVEL);
        }
        unitTypeLevelSpecRepository
                .findByUnitType_IdAndLevel(unitType.getId(), level)
                .ifPresentOrElse(
                        spec -> {
                            if (values.hasNoValues()) {
                                unitTypeLevelSpecRepository.delete(spec);
                                return;
                            }
                            validateComplete(values);
                            spec.update(
                                    values.attackPower(),
                                    values.defensePower(),
                                    values.trainCostFood(),
                                    values.requiredBarracksLevel());
                        },
                        () -> {
                            if (values.hasNoValues()) return;
                            validateComplete(values);
                            unitTypeLevelSpecRepository.save(
                                    UnitTypeLevelSpec.builder()
                                            .unitType(unitType)
                                            .level(level)
                                            .attackPower(values.attackPower())
                                            .defensePower(values.defensePower())
                                            .trainCostFood(values.trainCostFood())
                                            .requiredBarracksLevel(values.requiredBarracksLevel())
                                            .build());
                        });
    }

    // 훈련 스펙은 부분 입력을 허용하지 않는다 — 넷 다 있거나 넷 다 비어야 한다.
    private void validateComplete(UnitLevelValues values) {
        if (values.attackPower() == null
                || values.defensePower() == null
                || values.trainCostFood() == null
                || values.requiredBarracksLevel() == null) {
            throw new CustomException(ErrorCode.INCOMPLETE_UNIT_LEVEL_SPEC);
        }
    }

    private UnitType findOrThrow(Long unitTypeId) {
        return unitTypeRepository
                .findById(unitTypeId)
                .orElseThrow(() -> new CustomException(ErrorCode.UNIT_TYPE_NOT_FOUND));
    }

    private String blankToNull(String v) {
        return (v != null && !v.isBlank()) ? v.trim() : null;
    }
}
