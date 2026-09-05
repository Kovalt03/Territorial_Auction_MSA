package com.territorial.user.client;

import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** season-service 트로피 조회 위임 — 공개 프로필의 트로피 점수만 읽는다. */
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

    public Optional<Trophy> getTrophy(Long userId) {
        return Optional.ofNullable(
                restClient.get().uri(ROOT + "/{userId}", userId).retrieve().body(Trophy.class));
    }

    public record Trophy(Long userId, int score, String league) {}
}
