package com.territorial.auction.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;

@ExtendWith(MockitoExtension.class)
class EventBusPublisherTest {

    @Mock private RedissonClient redissonClient;
    @Mock private AuctionOutboxEventRepository outboxEventRepository;
    @Mock private RTopic rtopic;

    @DisplayName("publish — durable은 아웃박스 저장(원자적), 실시간은 Redis 브로드캐스트")
    @Test
    void publish_recordsOutboxAndBroadcastsRealtime() {
        // 실 ObjectMapper로 직렬화 검증. 활성 트랜잭션 동기화가 없으므로 Redis는 즉시 발행 경로.
        EventBusPublisher publisher =
                new EventBusPublisher(redissonClient, new ObjectMapper(), outboxEventRepository);
        given(redissonClient.getTopic("auction.bid")).willReturn(rtopic);

        publisher.publish("auction.bid", Map.of("auctionId", 7, "price", 1100));

        // durable: 아웃박스에 topic·payload 저장
        ArgumentCaptor<AuctionOutboxEvent> captor =
                ArgumentCaptor.forClass(AuctionOutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        AuctionOutboxEvent saved = captor.getValue();
        assertThat(saved.getTopic()).isEqualTo("auction.bid");
        assertThat(saved.getPayload()).contains("\"auctionId\":7").contains("\"price\":1100");
        assertThat(saved.getPublishedAt()).isNull();

        // real-time: Redis 토픽으로 동일 JSON 브로드캐스트
        verify(rtopic).publish(anyString());
    }
}
