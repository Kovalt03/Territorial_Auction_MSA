package com.territorial.season.domain.season.service;

import com.territorial.season.domain.season.entity.Season;
import com.territorial.season.domain.season.repository.SeasonRepository;
import java.io.InputStream;
import java.time.LocalDateTime;
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
@Order(4)
@RequiredArgsConstructor
public class SeasonSeeder implements ApplicationRunner {

    private final SeasonRepository seasonRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Map<String, Object> entry = loadEntry();
        int seasonNumber = (Integer) entry.get("seasonNumber");

        if (seasonRepository.existsBySeasonNumber(seasonNumber)) {
            log.info("season#{} 이미 존재 — 건너뜀", seasonNumber);
            return;
        }

        Season season =
                Season.builder()
                        .seasonNumber(seasonNumber)
                        .startedAt(LocalDateTime.parse((String) entry.get("startedAt")))
                        .endedAt(LocalDateTime.parse((String) entry.get("endedAt")))
                        .build();
        seasonRepository.save(season);
        log.info("season 시드 완료. seasonNumber={}", seasonNumber);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadEntry() {
        Yaml yaml = new Yaml();
        try (InputStream is = getClass().getResourceAsStream("/db/season.yml")) {
            Map<String, Object> root = yaml.load(is);
            return (Map<String, Object>) root.get("season");
        } catch (Exception e) {
            throw new IllegalStateException("season.yml 로드 실패", e);
        }
    }
}
