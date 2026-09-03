package com.territorial.auction.domain.ranking.service;

import com.territorial.auction.domain.ranking.event.AuctionSettledEvent;
import com.territorial.auction.domain.ranking.event.TerritoryHoldStartedEvent;
import com.territorial.auction.global.client.SeasonGameEventClient;
import com.territorial.auction.global.client.SeasonQueryClient;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * auction-service의 Redis auction.settled 이벤트를 모놀리식 인프로세스 Spring 이벤트로 중계한다. 랭킹(경매 소비·영토 보유) 리스너가
 * @TransactionalEventListener(AFTER_COMMIT)라 활성 트랜잭션 안에서 발행돼야 하므로 @Transactional로 감싼다. 시즌 XP·미션은
 * season-service로 위임(game-events)하고, 랭킹 시즌 귀속은 활성 시즌을 조회해 채운다.
 */
@Service
@RequiredArgsConstructor
public class AuctionSettlementRelayService {

    private final SeasonQueryClient seasonQueryClient;
    private final SeasonGameEventClient seasonGameEventClient;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void relay(Long winnerId, Long territoryId, String grade, int finalPrice) {
        // XP·미션은 season-service가 활성 시즌 판단까지 자체 수행한다.
        seasonGameEventClient.sendGameEvent(winnerId, "AUCTION_WIN");

        LocalDateTime now = LocalDateTime.now();
        Long seasonId =
                seasonQueryClient
                        .getActiveSeason()
                        .map(SeasonQueryClient.ActiveSeason::seasonId)
                        .orElse(null);
        if (seasonId == null) return; // 시즌 외 기간엔 랭킹·시즌 귀속 없음
        eventPublisher.publishEvent(new AuctionSettledEvent(winnerId, seasonId, finalPrice));
        eventPublisher.publishEvent(
                new TerritoryHoldStartedEvent(winnerId, seasonId, territoryId, grade, now));
    }
}
