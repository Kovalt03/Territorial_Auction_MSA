package com.territorial.auction.domain.building.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.territorial.auction.domain.building.service.UserBootstrapService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserCreatedSubscriber {

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;
    private final UserBootstrapService userBootstrapService;

    @PostConstruct
    public void subscribe() {
        redissonClient
                .getTopic("user.created")
                .addListener(
                        String.class,
                        (channel, json) -> {
                            try {
                                UserCreatedEvent event =
                                        objectMapper.readValue(json, UserCreatedEvent.class);
                                userBootstrapService.bootstrap(
                                        event.userId(),
                                        event.username(),
                                        event.email(),
                                        event.nickname());
                            } catch (Exception e) {
                                log.error("[UserCreatedSubscriber] 처리 실패: {}", json, e);
                            }
                        });
    }

    private record UserCreatedEvent(Long userId, String username, String email, String nickname) {}
}
