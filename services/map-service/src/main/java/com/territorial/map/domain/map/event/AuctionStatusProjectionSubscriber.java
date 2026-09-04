package com.territorial.map.domain.map.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.territorial.map.domain.map.service.TerritoryAuctionStatusService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * map 읽기 프로젝션 구독기. auction-service의 auction.opened/bid/closed(Kafka `auction-events`)로
 * territory_auction_status를 갱신한다. durable 경로 — 페이로드는 자체 record(필드명만 일치).
 *
 * <p>groupId는 모놀리식 시절과 동일(backend-map-projection) — 커밋된 offset을 상속해 전체 리플레이를 피한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionStatusProjectionSubscriber {

    private final ObjectMapper objectMapper;
    private final TerritoryAuctionStatusService statusService;

    @KafkaListener(topics = "auction-events", groupId = "backend-map-projection")
    public void handle(
            @Payload String json,
            @Header(name = "event-topic", required = false) byte[] eventTopicHeader) {
        String topic =
                eventTopicHeader != null
                        ? new String(eventTopicHeader, StandardCharsets.UTF_8)
                        : "";
        try {
            switch (topic) {
                case "auction.opened" -> {
                    OpenedEvent e = objectMapper.readValue(json, OpenedEvent.class);
                    statusService.open(e.territoryId(), e.auctionId(), e.currentPrice(), e.endAt());
                }
                case "auction.bid" -> {
                    BidEvent e = objectMapper.readValue(json, BidEvent.class);
                    statusService.updateBid(e.auctionId(), e.currentPrice(), e.endAt());
                }
                case "auction.closed" -> {
                    ClosedEvent e = objectMapper.readValue(json, ClosedEvent.class);
                    statusService.close(e.auctionId());
                }
                default -> {
                    /* settled 등은 프로젝션 관심 밖 */
                }
            }
        } catch (Exception ex) {
            log.error("[AuctionStatusProjection] 처리 실패: topic={}, payload={}", topic, json, ex);
            throw new IllegalStateException("auction 프로젝션 처리 실패", ex);
        }
    }

    private record OpenedEvent(
            Long auctionId, Long territoryId, int currentPrice, LocalDateTime endAt) {}

    private record BidEvent(Long auctionId, int currentPrice, LocalDateTime endAt) {}

    private record ClosedEvent(Long auctionId, Long territoryId) {}
}
