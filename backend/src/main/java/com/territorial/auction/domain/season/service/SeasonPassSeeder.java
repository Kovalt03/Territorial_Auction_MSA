package com.territorial.auction.domain.season.service;

import com.territorial.auction.domain.season.entity.SeasonPass;
import com.territorial.auction.domain.season.repository.SeasonPassRepository;
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
@Order(7)
@RequiredArgsConstructor
public class SeasonPassSeeder implements ApplicationRunner {

    private final SeasonPassRepository seasonPassRepository;

    // 관리자가 값을 편집할 수 있으므로 yml은 초기값 역할만 한다 — 없는 패스만 새로 만든다.
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Map<String, Object>> entries = loadEntries();
        entries.forEach(this::createIfAbsent);
        log.info("season_passes 시드 확인 완료. 건수={}", entries.size());
    }

    private void createIfAbsent(Map<String, Object> m) {
        String name = (String) m.get("name");
        if (seasonPassRepository.findByName(name).isPresent()) return;
        seasonPassRepository.save(toEntity(m));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadEntries() {
        Yaml yaml = new Yaml();
        try (InputStream is = getClass().getResourceAsStream("/db/season-passes.yml")) {
            Map<String, Object> root = yaml.load(is);
            return (List<Map<String, Object>>) root.get("seasonPasses");
        } catch (Exception e) {
            throw new IllegalStateException("season-passes.yml 로드 실패", e);
        }
    }

    private SeasonPass toEntity(Map<String, Object> m) {
        return SeasonPass.builder()
                .name((String) m.get("name"))
                .costAp((Integer) m.get("costAp"))
                .durationDays((Integer) m.get("durationDays"))
                .islandBonusPct((Integer) m.get("islandBonusPct"))
                .extraBuilders((Integer) m.get("extraBuilders"))
                .buildTimeReductionPct((Integer) m.get("buildTimeReductionPct"))
                .taxExemptBonus((Integer) m.get("taxExemptBonus"))
                .build();
    }
}
