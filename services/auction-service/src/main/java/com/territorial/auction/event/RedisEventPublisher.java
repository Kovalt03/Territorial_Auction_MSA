package com.territorial.auction.event;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

/**
 * Redisson RTopic 기반 이벤트 발행. RedissonClient 빈은 common의 RedissonConfig가 제공.
 *
 * <p>TODO: 페이로드 직렬화 코덱 확인 — record 직렬화가 기본 코덱(Kryo 등)에서 되는지, 안 되면 JsonJacksonCodec로 토픽 코덱 지정.
 */
@Component
@RequiredArgsConstructor
public class RedisEventPublisher implements EventPublisher {

    private final RedissonClient redissonClient;

    @Override
    public void publish(String topic, Object payload) {
        redissonClient.getTopic(topic).publish(payload);
    }
}
