package com.territorial.auction.domain.map.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.territorial.auction.domain.map.service.TerritoryAuctionStatusService;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

/**
 * map 읽기 프로젝션 구독기. auction-service의 auction.opened/bid/closed 이벤트로 territory_auction_status를 갱신한다.
 * auction 도메인이 삭제돼도 살아남도록 페이로드는 자체 record로 정의한다(필드명만 일치하면 됨).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionStatusProjectionSubscriber {

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;
    private final TerritoryAuctionStatusService statusService;

    @PostConstruct
    public void subscribe() {
        redissonClient
                .getTopic("auction.opened")
                .addListener(String.class, (ch, json) -> handleOpened(json));
        redissonClient
                .getTopic("auction.bid")
                .addListener(String.class, (ch, json) -> handleBid(json));
        redissonClient
                .getTopic("auction.closed")
                .addListener(String.class, (ch, json) -> handleClosed(json));
        log.info("[AuctionStatusProjection] 구독 시작: auction.opened, auction.bid, auction.closed");
    }

    private void handleOpened(String json) {
        try {
            OpenedEvent e = objectMapper.readValue(json, OpenedEvent.class);
            statusService.open(e.territoryId(), e.auctionId(), e.currentPrice(), e.endAt());
        } catch (Exception ex) {
            log.error("[AuctionStatusProjection] opened 처리 실패: {}", json, ex);
        }
    }

    private void handleBid(String json) {
        try {
            BidEvent e = objectMapper.readValue(json, BidEvent.class);
            statusService.updateBid(e.auctionId(), e.currentPrice(), e.endAt());
        } catch (Exception ex) {
            log.error("[AuctionStatusProjection] bid 처리 실패: {}", json, ex);
        }
    }

    private void handleClosed(String json) {
        try {
            ClosedEvent e = objectMapper.readValue(json, ClosedEvent.class);
            statusService.close(e.auctionId());
        } catch (Exception ex) {
            log.error("[AuctionStatusProjection] closed 처리 실패: {}", json, ex);
        }
    }

    private record OpenedEvent(
            Long auctionId, Long territoryId, int currentPrice, LocalDateTime endAt) {}

    private record BidEvent(Long auctionId, int currentPrice, LocalDateTime endAt) {}

    private record ClosedEvent(Long auctionId, Long territoryId) {}
}
