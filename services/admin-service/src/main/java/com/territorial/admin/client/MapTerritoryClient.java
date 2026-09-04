package com.territorial.admin.client;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * map-service 영토 조회·인계 위임(공유 커널 map 추출). 모놀리식 user·member·combat 소비자가 영토를 직접 조회하는 대신 이 계약을 호출한다.
 */
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

    public List<OwnerCount> getOwnerCounts(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        List<OwnerCount> result =
                restClient
                        .post()
                        .uri(ROOT + "/owner-counts")
                        .body(new OwnerIds(userIds))
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {});
        return result != null ? result : List.of();
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

    public List<Long> getOwnerTerritoryIds(Long userId) {
        List<Long> result =
                restClient
                        .get()
                        .uri(ROOT + "/owners/{userId}/ids", userId)
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {});
        return result != null ? result : List.of();
    }

    public void takeOver(Long territoryId, Long newOwnerId, Long formerOwnerId) {
        restClient
                .post()
                .uri(ROOT + "/{id}/takeover", territoryId)
                .body(new Takeover(newOwnerId, formerOwnerId))
                .retrieve()
                .toBodilessEntity();
    }

    public record OwnerCount(Long ownerId, long count) {}

    public record OwnerHolding(
            Long territoryId,
            String grade,
            int coordX,
            int coordY,
            String continentName,
            LocalDateTime occupiedAt,
            LocalDateTime occupiedUntil) {}

    public record OwnerHoldingPage(List<OwnerHolding> content, long totalElements) {}

    private record OwnerIds(List<Long> userIds) {}

    private record Takeover(Long newOwnerId, Long formerOwnerId) {}
}
