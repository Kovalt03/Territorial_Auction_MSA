package com.territorial.auction.domain.ranking.service;

import com.territorial.auction.domain.ranking.event.AuctionSettledEvent;
import com.territorial.auction.domain.ranking.event.TerritoryHoldStartedEvent;
import com.territorial.auction.domain.season.entity.Season;
import com.territorial.auction.domain.season.repository.SeasonRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * auction-service의 Redis auction.settled 이벤트를 모놀리식 인프로세스 Spring 이벤트로 중계한다. 랭킹(경매 소비·영토 보유)·시즌 XP·미션
 * 리스너가 @TransactionalEventListener(AFTER_COMMIT)라 활성 트랜잭션 안에서 발행돼야 하므로 @Transactional로 감싼다. 시즌 귀속은
 * 모놀리식이 소유하므로 여기서 현재 활성 시즌을 조회해 채운다.
 */
@Service
@RequiredArgsConstructor
public class AuctionSettlementRelayService {

    private final SeasonRepository seasonRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void relay(Long winnerId, Long territoryId, String grade, int finalPrice) {
        LocalDateTime now = LocalDateTime.now();
        Season season = seasonRepository.findActiveSeason(now).orElse(null);
        if (season == null) return; // 시즌 외 기간엔 랭킹·시즌 귀속 없음
        Long seasonId = season.getId();
        eventPublisher.publishEvent(new AuctionSettledEvent(winnerId, seasonId, finalPrice));
        eventPublisher.publishEvent(
                new TerritoryHoldStartedEvent(winnerId, seasonId, territoryId, grade, now));
    }
}
