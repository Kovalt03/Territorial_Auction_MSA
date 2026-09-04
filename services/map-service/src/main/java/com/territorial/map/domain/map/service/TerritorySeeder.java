package com.territorial.map.domain.map.service;

import com.territorial.map.domain.map.entity.Continent;
import com.territorial.map.domain.map.entity.Territory;
import com.territorial.map.domain.map.entity.TerritoryGrade;
import com.territorial.map.domain.map.repository.ContinentRepository;
import com.territorial.map.domain.map.repository.TerritoryGradeRepository;
import com.territorial.map.domain.map.repository.TerritoryRepository;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;
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
@Order(9)
@RequiredArgsConstructor
public class TerritorySeeder implements ApplicationRunner {

    // 등급 서열: 숫자가 클수록 높은 등급
    private static final Map<String, Integer> GRADE_RANK = Map.of("S", 4, "A", 3, "B", 2, "C", 1);

    private final TerritoryRepository territoryRepository;
    private final ContinentRepository continentRepository;
    private final TerritoryGradeRepository territoryGradeRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (territoryRepository.count() > 0) {
            log.info("territories 이미 존재 — 건너뜀");
            return;
        }

        TerritoryMapConfig config = loadConfig();
        Map<String, Continent> continentByName =
                continentRepository.findAll().stream()
                        .collect(Collectors.toMap(Continent::getName, Function.identity()));
        List<TerritoryGrade> allGrades = territoryGradeRepository.findAll();

        Random random = new Random();
        int mapSize = config.mapSize();
        List<Territory> territories = new ArrayList<>(mapSize * mapSize);

        for (int x = 1; x <= mapSize; x++) {
            for (int y = 1; y <= mapSize; y++) {
                Continent continent = continentByName.get(config.continentNameFor(x, y));
                TerritoryGrade grade = pickGrade(allGrades, continent.getGrade(), random);
                territories.add(
                        Territory.builder()
                                .coordX(x)
                                .coordY(y)
                                .continent(continent)
                                .grade(grade)
                                .build());
            }
        }

        territoryRepository.saveAll(territories);
        log.info("territories 시드 완료. 건수={}", territories.size());
    }

    /**
     * 대륙 최대 등급 이하의 TerritoryGrade 중에서 spawnRate 비율에 따라 랜덤 선택. 선택 가능한 등급만 골라 spawnRate를 재정규화한 뒤 뽑는다.
     */
    private TerritoryGrade pickGrade(
            List<TerritoryGrade> allGrades, String maxGrade, Random random) {
        int maxRank = GRADE_RANK.getOrDefault(maxGrade, 1);

        List<TerritoryGrade> eligible =
                allGrades.stream()
                        .filter(g -> GRADE_RANK.getOrDefault(g.getGrade(), 0) <= maxRank)
                        .toList();

        double totalRate = eligible.stream().mapToDouble(g -> g.getSpawnRate().doubleValue()).sum();

        double roll = random.nextDouble() * totalRate;
        double cumulative = 0;
        for (TerritoryGrade g : eligible) {
            cumulative += g.getSpawnRate().doubleValue();
            if (roll < cumulative) return g;
        }
        return eligible.get(eligible.size() - 1);
    }

    @SuppressWarnings("unchecked")
    private TerritoryMapConfig loadConfig() {
        Yaml yaml = new Yaml();
        try (InputStream is = getClass().getResourceAsStream("/db/territory-map.yml")) {
            Map<String, Object> root = yaml.load(is);
            Map<String, Object> map = (Map<String, Object>) root.get("territoryMap");

            int mapSize = (Integer) map.get("mapSize");

            List<Map<String, Object>> regionRows =
                    (List<Map<String, Object>>) map.get("continentRegions");
            List<ContinentRegion> regions =
                    regionRows.stream()
                            .map(
                                    r ->
                                            new ContinentRegion(
                                                    (String) r.get("continentName"),
                                                    (Integer) r.get("xMin"),
                                                    (Integer) r.get("xMax"),
                                                    (Integer) r.get("yMin"),
                                                    (Integer) r.get("yMax")))
                            .toList();

            return new TerritoryMapConfig(mapSize, regions);
        } catch (Exception e) {
            throw new IllegalStateException("territory-map.yml 로드 실패", e);
        }
    }

    private record ContinentRegion(String continentName, int xMin, int xMax, int yMin, int yMax) {
        boolean contains(int x, int y) {
            return x >= xMin && x <= xMax && y >= yMin && y <= yMax;
        }
    }

    private record TerritoryMapConfig(int mapSize, List<ContinentRegion> regions) {
        String continentNameFor(int x, int y) {
            return regions.stream()
                    .filter(r -> r.contains(x, y))
                    .map(ContinentRegion::continentName)
                    .findFirst()
                    .orElseThrow(
                            () ->
                                    new IllegalStateException(
                                            "continent 매핑 없음. x=" + x + ", y=" + y));
        }
    }
}
