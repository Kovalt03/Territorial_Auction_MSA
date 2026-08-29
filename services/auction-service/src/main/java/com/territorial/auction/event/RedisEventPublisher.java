package com.territorial.auction.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

/**
 * Redisson RTopic 기반 이벤트 발행. 페이로드를 JSON 문자열로 발행한다 — 서비스마다 이벤트 클래스가 따로 정의되므로(패키지 상이), 클래스 기반 코덱 대신
 * 필드 기반 JSON으로 서비스 간 통신. 소비자도 JSON 문자열을 자기 클래스로 역직렬화한다.
 */
@Component
@RequiredArgsConstructor
public class RedisEventPublisher implements EventPublisher {

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;

    @Override
    public void publish(String topic, Object payload) {
        try {
            redissonClient.getTopic(topic).publish(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("이벤트 직렬화 실패: topic=" + topic, e);
        }
    }
}
