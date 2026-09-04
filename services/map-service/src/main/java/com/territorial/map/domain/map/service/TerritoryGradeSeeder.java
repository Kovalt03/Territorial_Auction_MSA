package com.territorial.map.domain.map.service;

import com.territorial.map.domain.map.entity.TerritoryGrade;
import com.territorial.map.domain.map.repository.TerritoryGradeRepository;
import java.io.InputStream;
import java.math.BigDecimal;
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
@Order(1)
@RequiredArgsConstructor
public class TerritoryGradeSeeder implements ApplicationRunner {

    private final TerritoryGradeRepository territoryGradeRepository;

    // yml을 단일 진실 공급원으로 삼는다 — 기존 행이 있으면 건너뛰지 않고 값을 맞춘다.
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Map<String, Object>> entries = loadEntries();
        entries.forEach(this::upsert);
        log.info("territory_grades 시드 동기화 완료. 건수={}", entries.size());
    }

    private void upsert(Map<String, Object> m) {
        String grade = (String) m.get("grade");
        territoryGradeRepository
                .findByGrade(grade)
                .ifPresentOrElse(
                        found ->
                                found.syncFromSeed(
                                        toBigDecimal(m.get("productionMultiplier")),
                                        toBigDecimal(m.get("auctionPriceMultiplier")),
                                        (Integer) m.get("preBuiltCount"),
                                        toBigDecimal(m.get("spawnRate")),
                                        (Integer) m.get("gridSize"),
                                        (Integer) m.get("zone1Radius"),
                                        (Integer) m.get("zone2Radius")),
                        () -> territoryGradeRepository.save(toEntity(m)));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadEntries() {
        Yaml yaml = new Yaml();
        try (InputStream is = getClass().getResourceAsStream("/db/territory-grades.yml")) {
            Map<String, Object> root = yaml.load(is);
            return (List<Map<String, Object>>) root.get("territoryGrades");
        } catch (Exception e) {
            throw new IllegalStateException("territory-grades.yml 로드 실패", e);
        }
    }

    private TerritoryGrade toEntity(Map<String, Object> m) {
        return TerritoryGrade.builder()
                .grade((String) m.get("grade"))
                .productionMultiplier(toBigDecimal(m.get("productionMultiplier")))
                .auctionPriceMultiplier(toBigDecimal(m.get("auctionPriceMultiplier")))
                .preBuiltCount((Integer) m.get("preBuiltCount"))
                .spawnRate(toBigDecimal(m.get("spawnRate")))
                .gridSize((Integer) m.get("gridSize"))
                .zone1Radius((Integer) m.get("zone1Radius"))
                .zone2Radius((Integer) m.get("zone2Radius"))
                .build();
    }

    private BigDecimal toBigDecimal(Object val) {
        if (val instanceof Double d) return BigDecimal.valueOf(d);
        if (val instanceof Integer i) return BigDecimal.valueOf(i);
        return new BigDecimal(val.toString());
    }
}
