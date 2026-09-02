package com.territorial.combat.domain.building.service;

import com.territorial.combat.domain.building.entity.IslandGrade;
import com.territorial.combat.domain.building.repository.IslandGradeRepository;
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
@Order(3)
@RequiredArgsConstructor
public class IslandGradeSeeder implements ApplicationRunner {

    private final IslandGradeRepository islandGradeRepository;

    // yml을 단일 진실 공급원으로 삼는다 — 기존 행이 있으면 건너뛰지 않고 값을 맞춘다.
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Map<String, Object>> entries = loadEntries();
        entries.forEach(this::upsert);
        log.info("island_grades 시드 동기화 완료. 건수={}", entries.size());
    }

    private void upsert(Map<String, Object> m) {
        String name = (String) m.get("name");
        islandGradeRepository
                .findByName(name)
                .ifPresentOrElse(
                        grade ->
                                grade.syncFromSeed(
                                        (Integer) m.get("gridSize"),
                                        (Integer) m.get("zone1Radius"),
                                        (Integer) m.get("zone2Radius"),
                                        (Integer) m.get("castleLevelRequired")),
                        () -> islandGradeRepository.save(toEntity(m)));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadEntries() {
        Yaml yaml = new Yaml();
        try (InputStream is = getClass().getResourceAsStream("/db/island-grades.yml")) {
            Map<String, Object> root = yaml.load(is);
            return (List<Map<String, Object>>) root.get("islandGrades");
        } catch (Exception e) {
            throw new IllegalStateException("island-grades.yml 로드 실패", e);
        }
    }

    private IslandGrade toEntity(Map<String, Object> m) {
        return IslandGrade.builder()
                .name((String) m.get("name"))
                .gridSize((Integer) m.get("gridSize"))
                .zone1Radius((Integer) m.get("zone1Radius"))
                .zone2Radius((Integer) m.get("zone2Radius"))
                .castleLevelRequired((Integer) m.get("castleLevelRequired"))
                .build();
    }
}
