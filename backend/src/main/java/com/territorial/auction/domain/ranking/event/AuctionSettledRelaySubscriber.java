package com.territorial.auction.domain.ranking.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.territorial.auction.domain.ranking.service.AuctionSettlementRelayService;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * auction-service의 auction.settled(Kafka `auction-events`)를 구독해 랭킹·시즌 인프로세스 이벤트로 중계한다. durable 경로 —
 * 낙찰 시 랭킹·시즌 XP·미션이 끊기지 않게 잇는 브리지. 페이로드는 필드명만 일치하면 되는 자체 record.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionSettledRelaySubscriber {

    private static final String SETTLED_TOPIC = "auction.settled";

    private final ObjectMapper objectMapper;
    private final AuctionSettlementRelayService relayService;

    @KafkaListener(topics = "auction-events", groupId = "backend-ranking-relay")
    public void handle(
            @Payload String json,
            @Header(name = "event-topic", required = false) byte[] eventTopicHeader) {
        String topic =
                eventTopicHeader != null
                        ? new String(eventTopicHeader, StandardCharsets.UTF_8)
                        : "";
        if (!SETTLED_TOPIC.equals(topic)) {
            return; // 랭킹 중계는 settled만 관심
        }
        try {
            SettledEvent e = objectMapper.readValue(json, SettledEvent.class);
            relayService.relay(e.winnerId(), e.territoryId(), e.grade(), e.finalPrice());
        } catch (Exception ex) {
            log.error("[AuctionSettledRelay] 중계 실패: {}", json, ex);
            throw new IllegalStateException("auction.settled 중계 실패", ex);
        }
    }

    private record SettledEvent(Long winnerId, Long territoryId, String grade, int finalPrice) {}
}
