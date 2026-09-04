package com.territorial.ranking.domain.ranking.scheduler;

import com.territorial.ranking.client.SeasonQueryClient;
import com.territorial.ranking.client.SeasonQueryClient.ActiveSeason;
import com.territorial.ranking.domain.ranking.service.RankingService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RankingBatchScheduler {

    private final RankingService rankingService;
    private final SeasonQueryClient seasonQueryClient;

    @Scheduled(fixedRate = 3600000)
    public void aggregateTerritoryHoldRanking() {
        Optional<ActiveSeason> seasonOpt = seasonQueryClient.getActiveSeason();
        if (seasonOpt.isEmpty()) {
            log.info("활성 시즌 없음. 영토 점유 랭킹 집계 스킵.");
            return;
        }
        Long seasonId = seasonOpt.get().seasonId();
        log.info("영토 점유 랭킹 배치 시작. seasonId={}", seasonId);
        rankingService.aggregateTerritoryHoldRanking(seasonId);
        log.info("영토 점유 랭킹 배치 완료. seasonId={}", seasonId);
    }
}
