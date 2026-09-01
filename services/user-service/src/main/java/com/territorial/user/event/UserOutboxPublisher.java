package com.territorial.user.event;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserOutboxPublisher {

    public static final String STREAM_KEY = "stream:user-events";
    private final UserOutboxEventRepository outboxEventRepository;
    private final StringRedisTemplate redisTemplate;

    @Scheduled(fixedDelayString = "${outbox.publish-delay-ms:1000}")
    @Transactional
    public void publishPending() {
        for (UserOutboxEvent event :
                outboxEventRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()) {
            try {
                redisTemplate
                        .opsForStream()
                        .add(
                                StreamRecords.string(
                                                Map.of(
                                                        "eventId",
                                                        event.getId(),
                                                        "topic",
                                                        event.getTopic(),
                                                        "payload",
                                                        event.getPayload()))
                                        .withStreamKey(STREAM_KEY));
                event.markPublished();
            } catch (RuntimeException e) {
                log.warn(
                        "User outbox 발행 실패. eventId={}, topic={}",
                        event.getId(),
                        event.getTopic(),
                        e);
                return;
            }
        }
    }
}
