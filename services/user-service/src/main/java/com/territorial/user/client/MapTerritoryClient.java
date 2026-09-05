package com.territorial.user.client;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** map-service 영토 조회 위임 — 보유 영토 수·목록·존재 검증만 읽는다(인계 등 쓰기 계약 제외). */
@Component
public class MapTerritoryClient {

    private static final String ROOT = "/internal/territories";
    private final RestClient restClient;

    public MapTerritoryClient(
            RestClient.Builder builder,
            @Value("${map-service.base-url}") String baseUrl,
            @Value("${internal-api.secret}") String internalApiSecret) {
        this.restClient =
                builder.baseUrl(baseUrl)
                        .defaultHeader("X-Internal-Service-Token", internalApiSecret)
                        .build();
    }

    public boolean exists(Long territoryId) {
        Boolean result =
                restClient
                        .get()
                        .uri(ROOT + "/{id}/exists", territoryId)
                        .retrieve()
                        .body(Boolean.class);
        return Boolean.TRUE.equals(result);
    }

    public long getOwnerCount(Long userId) {
        Long count =
                restClient
                        .get()
                        .uri(ROOT + "/owners/{userId}/count", userId)
                        .retrieve()
                        .body(Long.class);
        return count != null ? count : 0L;
    }

    public OwnerHoldingPage getOwnerHoldings(Long userId, int page, int size) {
        OwnerHoldingPage result =
                restClient
                        .get()
                        .uri(
                                builder ->
                                        builder.path(ROOT + "/owners/{userId}/holdings")
                                                .queryParam("page", page)
                                                .queryParam("size", size)
                                                .build(userId))
                        .retrieve()
                        .body(OwnerHoldingPage.class);
        return result != null ? result : new OwnerHoldingPage(List.of(), 0);
    }

    public record OwnerHolding(
            Long territoryId,
            String grade,
            int coordX,
            int coordY,
            String continentName,
            LocalDateTime occupiedAt,
            LocalDateTime occupiedUntil) {}

    public record OwnerHoldingPage(List<OwnerHolding> content, long totalElements) {}
}
