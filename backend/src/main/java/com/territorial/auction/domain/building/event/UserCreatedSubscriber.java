package com.territorial.auction.domain.building.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.territorial.auction.domain.building.service.UserBootstrapService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserCreatedSubscriber {

    private static final String STREAM_KEY = "stream:user-events";
    private static final String GROUP = "backend-user-bootstrap";
    private static final String CONSUMER = "backend-1";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final UserBootstrapService userBootstrapService;

    @Scheduled(fixedDelayString = "${user-events.poll-delay-ms:1000}")
    @SuppressWarnings("unchecked") // Spring Data Stream read API가 generic varargs를 노출한다.
    public void poll() {
        if (!ensureConsumerGroup()) {
            return;
        }
        StreamOperations<String, Object, Object> streamOperations = redisTemplate.opsForStream();
        List<MapRecord<String, Object, Object>> pendingRecords =
                streamOperations.read(
                        Consumer.from(GROUP, CONSUMER),
                        StreamReadOptions.empty().count(20),
                        StreamOffset.create(STREAM_KEY, ReadOffset.from("0")));
        handleAll(pendingRecords);

        List<MapRecord<String, Object, Object>> newRecords =
                streamOperations.read(
                        Consumer.from(GROUP, CONSUMER),
                        StreamReadOptions.empty().count(20),
                        StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()));
        handleAll(newRecords);
    }

    private void handleAll(List<MapRecord<String, Object, Object>> records) {
        if (records != null) {
            records.forEach(this::handle);
        }
    }

    private boolean ensureConsumerGroup() {
        try {
            redisTemplate.opsForStream().createGroup(STREAM_KEY, ReadOffset.from("0-0"), GROUP);
            return true;
        } catch (DataAccessException e) {
            return hasBusyGroupCause(e);
        }
    }

    private boolean hasBusyGroupCause(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains("BUSYGROUP")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void handle(MapRecord<String, Object, Object> record) {
        String json = String.valueOf(record.getValue().get("payload"));
        try {
            UserCreatedEvent event = objectMapper.readValue(json, UserCreatedEvent.class);
            userBootstrapService.bootstrap(
                    event.userId(), event.username(), event.email(), event.nickname());
            redisTemplate.opsForStream().acknowledge(STREAM_KEY, GROUP, record.getId());
        } catch (Exception e) {
            log.error(
                    "[UserCreatedSubscriber] 처리 실패: recordId={}, payload={}",
                    record.getId(),
                    json,
                    e);
        }
    }

    private record UserCreatedEvent(Long userId, String username, String email, String nickname) {}
}
