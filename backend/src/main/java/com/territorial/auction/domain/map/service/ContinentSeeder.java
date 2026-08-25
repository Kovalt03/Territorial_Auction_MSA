package com.territorial.auction.domain.map.service;

import com.territorial.auction.domain.map.entity.Continent;
import com.territorial.auction.domain.map.repository.ContinentRepository;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.Yaml;

@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class ContinentSeeder implements ApplicationRunner {

    private final ContinentRepository continentRepository;

    @Value("classpath:continents.yml")
    private Resource continentsResource;

    private record SeedEntry(
            String name,
            String themeColor,
            String displayName,
            String grade,
            Integer minTrophyRequired,
            String description) {}

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (continentRepository.count() == 0) {
            seedAll();
        } else if (continentRepository.existsByDisplayNameIsNull()) {
            migrateDisplayData();
        } else {
            log.info("continents 이미 최신 데이터 — 건너뜀");
        }
    }

    @SuppressWarnings("unchecked")
    private List<SeedEntry> loadSeedEntries() {
        Yaml yaml = new Yaml();
        try (InputStream is = continentsResource.getInputStream()) {
            Map<String, Object> root = yaml.load(is);
            List<Map<String, Object>> list = (List<Map<String, Object>>) root.get("continents");
            return list.stream()
                    .map(
                            m ->
                                    new SeedEntry(
                                            (String) m.get("name"),
                                            (String) m.get("themeColor"),
                                            (String) m.get("displayName"),
                                            (String) m.get("grade"),
                                            (Integer) m.get("minTrophyRequired"),
                                            (String) m.get("description")))
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("continents.yml 로드 실패", e);
        }
    }

    private void seedAll() {
        List<Continent> continents =
                loadSeedEntries().stream()
                        .map(
                                e ->
                                        Continent.builder()
                                                .name(e.name())
                                                .themeColor(e.themeColor())
                                                .displayName(e.displayName())
                                                .grade(e.grade())
                                                .minTrophyRequired(e.minTrophyRequired())
                                                .description(e.description())
                                                .build())
                        .toList();
        continentRepository.saveAll(continents);
        log.info("continents 시드 완료 ({}건)", continents.size());
    }

    private void migrateDisplayData() {
        Map<String, SeedEntry> byName =
                loadSeedEntries().stream().collect(Collectors.toMap(SeedEntry::name, e -> e));
        continentRepository
                .findAll()
                .forEach(
                        c -> {
                            SeedEntry e = byName.get(c.getName());
                            if (e != null) {
                                c.updateDisplayData(
                                        e.displayName(),
                                        e.grade(),
                                        e.minTrophyRequired(),
                                        e.description());
                            }
                        });
        log.info("continents 표시 데이터 마이그레이션 완료");
    }
}
