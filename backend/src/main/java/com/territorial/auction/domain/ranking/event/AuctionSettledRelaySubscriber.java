package com.territorial.auction.domain.ranking.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.territorial.auction.domain.ranking.service.AuctionSettlementRelayService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

/**
 * auction-service의 auction.settled(Redis)를 구독해 랭킹·시즌 인프로세스 이벤트로 중계한다. auction 도메인이 모놀리식에서 삭제됐으므로,
 * 낙찰 시 랭킹(경매 소비·영토 보유 트로피)·시즌 XP·미션이 끊기지 않게 잇는 브리지. 페이로드는 필드명만 일치하면 되는 자체 record.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionSettledRelaySubscriber {

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;
    private final AuctionSettlementRelayService relayService;

    @PostConstruct
    public void subscribe() {
        redissonClient
                .getTopic("auction.settled")
                .addListener(String.class, (ch, json) -> handle(json));
        log.info("[AuctionSettledRelay] 구독 시작: auction.settled → 랭킹·시즌 중계");
    }

    private void handle(String json) {
        try {
            SettledEvent e = objectMapper.readValue(json, SettledEvent.class);
            relayService.relay(e.winnerId(), e.territoryId(), e.grade(), e.finalPrice());
        } catch (Exception ex) {
            log.error("[AuctionSettledRelay] 중계 실패: {}", json, ex);
        }
    }

    private record SettledEvent(Long winnerId, Long territoryId, String grade, int finalPrice) {}
}
