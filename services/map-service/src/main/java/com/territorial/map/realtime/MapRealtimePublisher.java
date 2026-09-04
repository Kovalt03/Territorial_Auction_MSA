package com.territorial.map.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.territorial.map.domain.map.dto.MapUpdateBroadcast;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

/**
 * 맵 갱신 실시간 발행. 클라이언트 WS(/ws)는 모놀리식이 소유하므로, map-service는 Redis pub/sub 토픽 map.update에만 발행하고 모놀리식의
 * MapRealtimeSubscriber가 STOMP(/sub/map/update)로 relay한다(실시간 아키텍처 결정 A). 페이로드는 JSON 문자열 —
 * auction-service 이벤트와 동일 규약(필드 기반).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MapRealtimePublisher {

    public static final String TOPIC = "map.update";

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;

    public void publish(MapUpdateBroadcast update) {
        try {
            redissonClient.getTopic(TOPIC).publish(objectMapper.writeValueAsString(update));
        } catch (JsonProcessingException e) {
            log.error("[MapRealtime] 직렬화 실패 territoryId={}", update.territoryId(), e);
        }
    }
}
