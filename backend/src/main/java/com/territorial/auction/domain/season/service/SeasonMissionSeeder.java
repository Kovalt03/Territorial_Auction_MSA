package com.territorial.auction.domain.season.service;

import com.territorial.auction.domain.season.entity.Season;
import com.territorial.auction.domain.season.entity.SeasonMission;
import com.territorial.auction.domain.season.entity.SeasonMission.MissionPeriod;
import com.territorial.auction.domain.season.entity.SeasonMission.MissionTrigger;
import com.territorial.auction.domain.season.repository.SeasonMissionRepository;
import com.territorial.auction.domain.season.repository.SeasonRepository;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.Yaml;

@Slf4j
@Component
@Order(9)
@RequiredArgsConstructor
public class SeasonMissionSeeder implements ApplicationRunner {

    private final SeasonMissionRepository seasonMissionRepository;
    private final SeasonRepository seasonRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        Optional<Season> seasonOpt = seasonRepository.findActiveSeason(LocalDateTime.now());
        if (seasonOpt.isEmpty()) {
            log.warn("활성 시즌 없음 — 시즌 미션 시드 건너뜀");
            return;
        }

        Season season = seasonOpt.get();
        if (seasonMissionRepository.existsBySeason_Id(season.getId())) {
            return;
        }

        List<SeasonMission> missions = loadMissions(season);
        seasonMissionRepository.saveAll(missions);
        log.info("시즌 미션 시드 완료. seasonId={}, count={}", season.getId(), missions.size());
    }

    @SuppressWarnings("unchecked")
    private List<SeasonMission> loadMissions(Season season) throws Exception {
        ClassPathResource resource = new ClassPathResource("db/season-missions.yml");
        try (InputStream inputStream = resource.getInputStream()) {
            Map<String, Object> data = new Yaml().load(inputStream);
            List<Map<String, Object>> rows = (List<Map<String, Object>>) data.get("missions");
            return rows.stream()
                    .map(
                            row ->
                                    SeasonMission.builder()
                                            .season(season)
                                            .code((String) row.get("code"))
                                            .title((String) row.get("title"))
                                            .description((String) row.get("description"))
                                            .period(
                                                    MissionPeriod.valueOf(
                                                            (String) row.get("period")))
                                            .triggerType(
                                                    MissionTrigger.valueOf(
                                                            (String) row.get("triggerType")))
                                            .goalCount((Integer) row.get("goalCount"))
                                            .xpReward((Integer) row.get("xpReward"))
                                            .sortOrder((Integer) row.get("sortOrder"))
                                            .build())
                    .toList();
        }
    }
}
