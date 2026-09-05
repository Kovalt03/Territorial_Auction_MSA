package com.territorial.social.client;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** map-service 영토 조회 위임 — 멤버 통계용 소유자별 영토 수 배치 집계만 읽는다. */
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

    public record OwnerCount(Long ownerId, long count) {}

    private record OwnerIds(List<Long> userIds) {}
}
