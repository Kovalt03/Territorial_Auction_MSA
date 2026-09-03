package com.territorial.ranking.client;

import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** season-service 트로피 조회. nickname은 ranking이 user-service로 따로 붙이므로 userId·score·league만 받는다. */
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

    public List<Trophy> getRanking(int page, int size) {
        List<Trophy> response =
                restClient
                        .get()
                        .uri(
                                b ->
                                        b.path(ROOT + "/ranking")
                                                .queryParam("page", page)
                                                .queryParam("size", size)
                                                .build())
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {});
        return response != null ? response : List.of();
    }

    public long countAbove(int score) {
        Long value =
                restClient
                        .get()
                        .uri(b -> b.path(ROOT + "/count-above").queryParam("score", score).build())
                        .retrieve()
                        .body(Long.class);
        return value != null ? value : 0L;
    }

    public List<Trophy> getBand(int lower, int upper, int page, int size) {
        List<Trophy> response =
                restClient
                        .get()
                        .uri(
                                b ->
                                        b.path(ROOT + "/band")
                                                .queryParam("lower", lower)
                                                .queryParam("upper", upper)
                                                .queryParam("page", page)
                                                .queryParam("size", size)
                                                .build())
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {});
        return response != null ? response : List.of();
    }

    public long countBand(int score, int upper) {
        Long value =
                restClient
                        .get()
                        .uri(
                                b ->
                                        b.path(ROOT + "/count-band")
                                                .queryParam("score", score)
                                                .queryParam("upper", upper)
                                                .build())
                        .retrieve()
                        .body(Long.class);
        return value != null ? value : 0L;
    }

    public record Trophy(Long userId, int score, String league) {}
}
