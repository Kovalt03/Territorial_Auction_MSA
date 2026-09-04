package com.territorial.ranking.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * season-service 게임 이벤트 위임. 경매 낙찰 시 XP 적립·미션 진행을 season-service에 맡긴다(활성 시즌 판단은 season이 자체 수행).
 * best-effort — season-service 장애가 랭킹 집계를 막지 않도록 실패를 흡수한다.
 */
@Slf4j
@Component
public class SeasonGameEventClient {

    private final RestClient restClient;

    public SeasonGameEventClient(
            RestClient.Builder builder,
            @Value("${season-service.base-url}") String baseUrl,
            @Value("${internal-api.secret}") String internalApiSecret) {
        this.restClient =
                builder.baseUrl(baseUrl)
                        .defaultHeader("X-Internal-Service-Token", internalApiSecret)
                        .build();
    }

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
