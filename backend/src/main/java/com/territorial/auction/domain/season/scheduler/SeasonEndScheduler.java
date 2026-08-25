package com.territorial.auction.domain.season.scheduler;

import com.territorial.auction.domain.season.service.SeasonEndBatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SeasonEndScheduler {

    private final SeasonEndBatchService seasonEndBatchService;

    @Scheduled(fixedDelay = 60_000)
    public void checkSeasonEnd() {
        seasonEndBatchService.runIfSeasonEnded();
    }
}
