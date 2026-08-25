package com.territorial.auction.domain.ranking.scheduler;

import com.territorial.auction.domain.ranking.service.RankingService;
import com.territorial.auction.domain.season.entity.Season;
import com.territorial.auction.domain.season.repository.SeasonRepository;
import java.time.LocalDateTime;
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
    private final SeasonRepository seasonRepository;

    @Scheduled(fixedRate = 3600000)
    public void aggregateTerritoryHoldRanking() {
        Optional<Season> seasonOpt = seasonRepository.findActiveSeason(LocalDateTime.now());
        if (seasonOpt.isEmpty()) {
            log.info("활성 시즌 없음. 영토 점유 랭킹 집계 스킵.");
            return;
        }
        Long seasonId = seasonOpt.get().getId();
        log.info("영토 점유 랭킹 배치 시작. seasonId={}", seasonId);
        rankingService.aggregateTerritoryHoldRanking(seasonId);
        log.info("영토 점유 랭킹 배치 완료. seasonId={}", seasonId);
    }
}
