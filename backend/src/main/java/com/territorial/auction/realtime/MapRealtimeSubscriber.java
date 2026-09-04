package com.territorial.auction.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * 실시간 허브. map-service가 발행하는 map.update(Redis pub/sub)를 구독해 클라이언트에 STOMP push한다. 클라이언트 WS(/ws)는 모놀리식
 * 소유이므로 여기서 relay한다(실시간 아키텍처 결정 A). 경매 낙찰에 의한 OCCUPIED 브로드캐스트는 AuctionRealtimeSubscriber가 담당하고,
 * 여기서는 공성 인계·점유 만료(IDLE)를 relay한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MapRealtimeSubscriber {

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;

    @PostConstruct
    public void subscribe() {
        redissonClient
                .getTopic("map.update")
                .addListener(String.class, (channel, json) -> handle(json));
        log.info("[MapRealtime] 구독 시작: map.update");
    }

    private void handle(String json) {
        try {
            MapUpdateBroadcast update = objectMapper.readValue(json, MapUpdateBroadcast.class);
            messagingTemplate.convertAndSend("/sub/map/update", update);
        } catch (Exception ex) {
            log.error("[MapRealtime] map.update 처리 실패: {}", json, ex);
        }
    }
}
