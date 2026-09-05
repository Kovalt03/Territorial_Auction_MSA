package com.territorial.social.client;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** season-service 트로피 조회 위임 — 멤버 통계용 userId별 트로피 점수 합계 배치만 읽는다. */
@Component
public class SeasonTrophyClient {

    private static final String ROOT = "/internal/trophies";
    private final RestClient restClient;

    public SeasonTrophyClient(
            RestClient.Builder builder,
            @Value("${season-service.base-url}") String baseUrl,
            @Value("${internal-api.secret}") String internalApiSecret) {
        this.restClient =
                builder.baseUrl(baseUrl)
                        .defaultHeader("X-Internal-Service-Token", internalApiSecret)
                        .build();
    }

    public List<UserScore> sumScores(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        List<UserScore> response =
                restClient
                        .post()
                        .uri(ROOT + "/sum")
                        .body(userIds)
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {});
        return response != null ? response : List.of();
    }

    public record UserScore(Long userId, long totalScore) {}
}
