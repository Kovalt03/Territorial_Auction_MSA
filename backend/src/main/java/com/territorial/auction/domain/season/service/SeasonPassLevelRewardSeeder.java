package com.territorial.auction.domain.season.service;

import com.territorial.auction.domain.item.entity.Item;
import com.territorial.auction.domain.season.SeasonPassPolicy;
import com.territorial.auction.domain.season.entity.Season;
import com.territorial.auction.domain.season.entity.SeasonPassLevelReward;
import com.territorial.auction.domain.season.repository.SeasonPassLevelRewardRepository;
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
@Order(8)
@RequiredArgsConstructor
public class SeasonPassLevelRewardSeeder implements ApplicationRunner {

    private final SeasonPassLevelRewardRepository seasonPassLevelRewardRepository;
    private final SeasonRepository seasonRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        Optional<Season> seasonOpt = seasonRepository.findActiveSeason(LocalDateTime.now());
        if (seasonOpt.isEmpty()) {
            log.warn("활성 시즌 없음 — 시즌 패스 레벨 보상 시드 건너뜀");
            return;
        }

        Season season = seasonOpt.get();
        if (seasonPassLevelRewardRepository.existsBySeason_Id(season.getId())) {
            return;
        }

        List<SeasonPassLevelReward> rewards = loadRewards(season);
        seasonPassLevelRewardRepository.saveAll(rewards);
        log.info("시즌 패스 레벨 보상 시드 완료. seasonId={}, count={}", season.getId(), rewards.size());
    }

    @SuppressWarnings("unchecked")
    private List<SeasonPassLevelReward> loadRewards(Season season) throws Exception {
        ClassPathResource resource = new ClassPathResource("db/season-pass-rewards.yml");
        try (InputStream inputStream = resource.getInputStream()) {
            Map<String, Object> data = new Yaml().load(inputStream);
            List<Map<String, Object>> rows = (List<Map<String, Object>>) data.get("rewards");
            return rows.stream()
                    .filter(
                            row -> {
                                int level = (Integer) row.get("level");
                                if (level < 1 || level > SeasonPassPolicy.MAX_LEVEL) {
                                    log.warn(
                                            "유효 범위 초과 레벨 보상 건너뜀. level={}, maxLevel={}",
                                            level,
                                            SeasonPassPolicy.MAX_LEVEL);
                                    return false;
                                }
                                return true;
                            })
                    .map(
                            row ->
                                    SeasonPassLevelReward.builder()
                                            .season(season)
                                            .level((Integer) row.get("level"))
                                            .track(
                                                    SeasonPassLevelReward.RewardTrack.valueOf(
                                                            (String)
                                                                    row.getOrDefault(
                                                                            "track", "FREE")))
                                            .rewardName((String) row.get("rewardName"))
                                            .rewardKind(
                                                    SeasonPassLevelReward.RewardKind.valueOf(
                                                            (String)
                                                                    row.getOrDefault(
                                                                            "rewardKind", "ITEM")))
                                            .itemType(parseItemType((String) row.get("itemType")))
                                            .quantity((Integer) row.getOrDefault("quantity", 1))
                                            .build())
                    .toList();
        }
    }

    private Item.ItemType parseItemType(String value) {
        return value == null ? null : Item.ItemType.valueOf(value);
    }
}
