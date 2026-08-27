package com.territorial.auction.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.territorial.auction.domain.map.dto.MapUpdateBroadcast;
import com.territorial.auction.domain.notification.entity.NotificationLog.NotificationType;
import com.territorial.auction.domain.notification.service.NotificationService;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * 실시간 허브. auction-service가 발행하는 auction.bid·auction.settled 이벤트를 구독해 클라이언트에 STOMP push +
 * 알림을 남긴다. 클라이언트 WS는 모놀리식(/ws)이 소유하므로 여기서 push한다. (실시간 아키텍처 결정 A)
 *
 * <p>auction 도메인이 삭제돼도 살아남도록 자체 DTO를 쓴다(map의 MapUpdateBroadcast는 map 소유라 유지됨).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionRealtimeSubscriber {

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationService notificationService;

    @PostConstruct
    public void subscribe() {
        redissonClient.getTopic("auction.bid").addListener(String.class, (ch, json) -> handleBid(json));
        redissonClient
                .getTopic("auction.settled")
                .addListener(String.class, (ch, json) -> handleSettled(json));
        log.info("[AuctionRealtime] 구독 시작: auction.bid, auction.settled");
    }

    private void handleBid(String json) {
        try {
            BidEvent e = objectMapper.readValue(json, BidEvent.class);
            messagingTemplate.convertAndSend("/sub/auction/" + e.auctionId(), e);
        } catch (Exception ex) {
            log.error("[AuctionRealtime] bid 처리 실패: {}", json, ex);
        }
    }

    private void handleSettled(String json) {
        try {
            SettledEvent e = objectMapper.readValue(json, SettledEvent.class);
            String coord = "(" + e.coordX() + ", " + e.coordY() + ")";

            // 낙찰자 — WIN 토스트 + 알림
            messagingTemplate.convertAndSend(
                    "/sub/user/" + e.winnerId() + "/auction-result",
                    new ResultAlert(
                            e.auctionId(), e.territoryId(), e.coordX(), e.coordY(), e.finalPrice(),
                            "WIN"));
            notificationService.sendNotification(
                    e.winnerId(),
                    NotificationType.AUCTION_WIN,
                    coord + " 영토를 낙찰받았습니다! 낙찰가 " + e.finalPrice() + " AP.");

            // 맵 갱신 브로드캐스트
            messagingTemplate.convertAndSend(
                    "/sub/map/update",
                    new MapUpdateBroadcast(
                            e.territoryId(), e.coordX(), e.coordY(), e.winnerId(), e.winnerNickname(),
                            "OCCUPIED"));

            // 차순위 — LOSE 토스트 + 알림
            ResultAlert lose =
                    new ResultAlert(
                            e.auctionId(), e.territoryId(), e.coordX(), e.coordY(), e.finalPrice(),
                            "LOSE");
            for (Long runnerUpId : e.runnerUpIds()) {
                messagingTemplate.convertAndSend("/sub/user/" + runnerUpId + "/auction-result", lose);
                notificationService.sendNotification(
                        runnerUpId, NotificationType.AUCTION_LOSE, coord + " 영토 경매에서 낙찰에 실패했습니다.");
            }
        } catch (Exception ex) {
            log.error("[AuctionRealtime] settled 처리 실패: {}", json, ex);
        }
    }

    // auction-service가 발행하는 JSON과 필드명 일치 (필드 기반 역직렬화)
    private record BidEvent(
            Long auctionId,
            int currentPrice,
            Long bidderId,
            String bidderNickname,
            LocalDateTime bidAt,
            LocalDateTime endAt) {}

    private record SettledEvent(
            Long auctionId,
            Long territoryId,
            int coordX,
            int coordY,
            Long winnerId,
            String winnerNickname,
            int finalPrice,
            String grade,
            List<Long> runnerUpIds) {}

    // /sub/user/{id}/auction-result 페이로드 (기존 AuctionResultAlert와 동일 형태)
    private record ResultAlert(
            Long auctionId,
            Long territoryId,
            int coordX,
            int coordY,
            int finalPrice,
            String result) {}
}
