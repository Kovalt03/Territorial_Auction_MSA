package com.territorial.auction.global.client;

import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class RankingHoldClientImpl implements RankingHoldClient {

    private final RestClient restClient;

    public RankingHoldClientImpl(
            RestClient.Builder builder,
            @Value("${ranking-service.base-url}") String baseUrl,
            @Value("${internal-api.secret}") String internalApiSecret) {
        this.restClient =
                builder.baseUrl(baseUrl)
                        .defaultHeader("X-Internal-Service-Token", internalApiSecret)
                        .build();
    }

    // best-effort — ranking-service 장애가 영토 만료 처리를 막지 않도록 실패를 흡수한다.
    // 실패 시 점유 기록은 열린 채로 남아 다음 집계까지 현재 시각 기준으로 계산된다(경미한 과대 계상).
    @Override
    public void closeHold(Long userId, Long seasonId, Long territoryId, LocalDateTime heldUntil) {
        try {
            restClient
                    .post()
                    .uri("/internal/holds/close")
                    .body(new CloseHoldRequest(userId, seasonId, territoryId, heldUntil))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.error(
                    "영토 점유 종료 위임 실패. userId={}, seasonId={}, territoryId={}",
                    userId,
                    seasonId,
                    territoryId,
                    e);
        }
    }

    private record CloseHoldRequest(
            Long userId, Long seasonId, Long territoryId, LocalDateTime heldUntil) {}
}
