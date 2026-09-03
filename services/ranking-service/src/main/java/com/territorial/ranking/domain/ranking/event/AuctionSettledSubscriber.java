package com.territorial.ranking.domain.ranking.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.territorial.ranking.domain.ranking.service.RankingService;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * auction-service의 auction.settled(Kafka `auction-events`)를 직접 구독해 랭킹(경매 소비·영토 보유)과 시즌 XP·미션 위임을
 * 처리한다. 이전에는 모놀리식 relay가 인프로세스 이벤트로 중계했으나, ranking 추출로 ranking-service가 직접 소비한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionSettledSubscriber {

    private static final String SETTLED_TOPIC = "auction.settled";

    private final ObjectMapper objectMapper;
    private final RankingService rankingService;

    @KafkaListener(topics = "auction-events", groupId = "ranking-service-relay")
    public void handle(
            @Payload String json,
            @Header(name = "event-topic", required = false) byte[] eventTopicHeader) {
        String topic =
                eventTopicHeader != null
                        ? new String(eventTopicHeader, StandardCharsets.UTF_8)
                        : "";
        if (!SETTLED_TOPIC.equals(topic)) {
            return; // settled만 관심
        }
        try {
            SettledEvent e = objectMapper.readValue(json, SettledEvent.class);
            rankingService.onAuctionSettled(
                    e.winnerId(), e.territoryId(), e.grade(), e.finalPrice());
        } catch (Exception ex) {
            log.error("[AuctionSettledSubscriber] 처리 실패: {}", json, ex);
            throw new IllegalStateException("auction.settled 처리 실패", ex);
        }
    }

    private record SettledEvent(Long winnerId, Long territoryId, String grade, int finalPrice) {}
}
