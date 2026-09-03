package com.territorial.auction.global.client;

import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class SeasonTrophyClientImpl implements SeasonTrophyClient {

    private static final String ROOT = "/internal/trophies";
    private final RestClient restClient;

    public SeasonTrophyClientImpl(
            RestClient.Builder builder,
            @Value("${season-service.base-url}") String baseUrl,
            @Value("${internal-api.secret}") String internalApiSecret) {
        this.restClient =
                builder.baseUrl(baseUrl)
                        .defaultHeader("X-Internal-Service-Token", internalApiSecret)
                        .build();
    }

    @Override
    public Optional<Trophy> getTrophy(Long userId) {
        return Optional.ofNullable(
                restClient
                        .get()
                        .uri(ROOT + "/{userId}", userId)
                        .retrieve()
                        .body(Trophy.class));
    }

    @Override
    public List<Trophy> getRanking(int page, int size) {
        List<Trophy> response =
                restClient
                        .get()
                        .uri(
                                builder ->
                                        builder.path(ROOT + "/ranking")
                                                .queryParam("page", page)
                                                .queryParam("size", size)
                                                .build())
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {});
        return response != null ? response : List.of();
    }

    @Override
    public long countAbove(int score) {
        Long value =
                restClient
                        .get()
                        .uri(
                                builder ->
                                        builder.path(ROOT + "/count-above")
                                                .queryParam("score", score)
                                                .build())
                        .retrieve()
                        .body(Long.class);
        return value != null ? value : 0L;
    }

    @Override
    public List<Trophy> getBand(int lower, int upper, int page, int size) {
        List<Trophy> response =
                restClient
                        .get()
                        .uri(
                                builder ->
                                        builder.path(ROOT + "/band")
                                                .queryParam("lower", lower)
                                                .queryParam("upper", upper)
                                                .queryParam("page", page)
                                                .queryParam("size", size)
                                                .build())
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {});
        return response != null ? response : List.of();
    }

    @Override
    public long countBand(int score, int upper) {
        Long value =
                restClient
                        .get()
                        .uri(
                                builder ->
                                        builder.path(ROOT + "/count-band")
                                                .queryParam("score", score)
                                                .queryParam("upper", upper)
                                                .build())
                        .retrieve()
                        .body(Long.class);
        return value != null ? value : 0L;
    }

    @Override
    public List<UserScore> sumScores(List<Long> userIds) {
        if (userIds.isEmpty()) {
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
}
