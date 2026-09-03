package com.territorial.auction.global.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class SeasonGameEventClientImpl implements SeasonGameEventClient {

    private final RestClient restClient;

    public SeasonGameEventClientImpl(
            RestClient.Builder builder,
            @Value("${season-service.base-url}") String baseUrl,
            @Value("${internal-api.secret}") String internalApiSecret) {
        this.restClient =
                builder.baseUrl(baseUrl)
                        .defaultHeader("X-Internal-Service-Token", internalApiSecret)
                        .build();
    }

    // XP·미션 적립은 best-effort — season-service 장애가 경매 정산·공성 이벤트 처리를 막지 않도록 실패를 흡수한다.
    @Override
    public void sendGameEvent(Long userId, String eventType) {
        try {
            restClient
                    .post()
                    .uri("/internal/seasons/game-events")
                    .body(new GameEventRequest(userId, eventType))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.error("시즌 게임 이벤트 위임 실패. userId={}, eventType={}", userId, eventType, e);
        }
    }

    private record GameEventRequest(Long userId, String eventType) {}
}
