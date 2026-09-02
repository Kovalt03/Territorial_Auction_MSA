package com.territorial.combat.domain.military.service;

import com.territorial.combat.domain.military.entity.UnitType;
import com.territorial.combat.domain.military.entity.UnitTypeLevelSpec;
import com.territorial.combat.domain.military.repository.UnitTypeLevelSpecRepository;
import com.territorial.combat.domain.military.repository.UnitTypeRepository;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.Yaml;

@Slf4j
@Component
@Order(6)
@RequiredArgsConstructor
public class UnitTypeSeeder implements ApplicationRunner {

    private final UnitTypeRepository unitTypeRepository;
    private final UnitTypeLevelSpecRepository unitTypeLevelSpecRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Map<String, Object>> entries = loadEntries();
        entries.forEach(this::createIfAbsent);
        log.info("unit_types 시드 확인 완료. 건수={}", entries.size());
    }

    @SuppressWarnings("unchecked")
    private void createIfAbsent(Map<String, Object> row) {
        String name = (String) row.get("name");
        if (unitTypeRepository.findByName(name).isPresent()) {
            return;
        }
        UnitType unitType =
                unitTypeRepository.save(
                        UnitType.builder()
                                .name(name)
                                .displayName((String) row.get("displayName"))
                                .icon((String) row.get("icon"))
                                .colorHex((String) row.get("colorHex"))
                                .attackPower((Integer) row.get("attackPower"))
                                .defensePower((Integer) row.get("defensePower"))
                                .costGp((Integer) row.get("costGp"))
                                .foodCost((Integer) row.get("foodCost"))
                                .buildingDamage((Integer) row.get("buildingDamage"))
                                .level((Integer) row.get("level"))
                                .build());
        ((List<Map<String, Object>>) row.getOrDefault("levelSpecs", List.of()))
                .forEach(spec -> unitTypeLevelSpecRepository.save(toSpec(unitType, spec)));
    }

    private UnitTypeLevelSpec toSpec(UnitType unitType, Map<String, Object> spec) {
        return UnitTypeLevelSpec.builder()
                .unitType(unitType)
                .level((Integer) spec.get("level"))
                .attackPower((Integer) spec.get("attackPower"))
                .defensePower((Integer) spec.get("defensePower"))
                .trainCostFood((Integer) spec.get("trainCostFood"))
                .requiredBarracksLevel((Integer) spec.get("requiredBarracksLevel"))
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadEntries() {
        Yaml yaml = new Yaml();
        try (InputStream input = getClass().getResourceAsStream("/db/unit-types.yml")) {
            Map<String, Object> root = yaml.load(input);
            return (List<Map<String, Object>>) root.get("unitTypes");
        } catch (Exception exception) {
            throw new IllegalStateException("unit-types.yml 로드 실패", exception);
        }
    }
}
