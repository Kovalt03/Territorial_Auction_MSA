package com.territorial.ranking.client;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** season-service 활성 시즌 조회. 랭킹은 활성 시즌 단위로 산출한다. */
@Component
public class SeasonQueryClient {

    private final RestClient restClient;

    public SeasonQueryClient(
            RestClient.Builder builder,
            @Value("${season-service.base-url}") String baseUrl,
            @Value("${internal-api.secret}") String internalApiSecret) {
        this.restClient =
                builder.baseUrl(baseUrl)
                        .defaultHeader("X-Internal-Service-Token", internalApiSecret)
                        .build();
    }

    public Optional<ActiveSeason> getActiveSeason() {
        return Optional.ofNullable(
                restClient
                        .get()
                        .uri("/internal/seasons/active")
                        .retrieve()
                        .body(ActiveSeason.class));
    }

    public record ActiveSeason(
            Long seasonId, Integer seasonNumber, LocalDateTime startedAt, LocalDateTime endedAt) {}
}
