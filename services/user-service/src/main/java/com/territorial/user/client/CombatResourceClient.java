package com.territorial.user.client;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** combat-service 자원 조회 위임 — 프로필·지갑 합성에 필요한 금고 GP·섬·유닛 수만 읽는다(쓰기 계약 제외). */
@Component
public class CombatResourceClient {

    private final RestClient restClient;

    public CombatResourceClient(
            RestClient.Builder builder,
            @Value("${combat-service.base-url}") String baseUrl,
            @Value("${internal-api.secret}") String internalApiSecret) {
        this.restClient =
                builder.baseUrl(baseUrl)
                        .defaultHeader("X-Internal-Service-Token", internalApiSecret)
                        .build();
    }

    public UserSummary getUserSummary(Long userId) {
        return restClient
                .get()
                .uri("/internal/combat/users/{userId}/summary", userId)
                .retrieve()
                .body(UserSummary.class);
    }

    public List<TerritoryUnitCount> getTerritoryUnitCounts(List<Long> territoryIds) {
        if (territoryIds.isEmpty()) {
            return List.of();
        }
        List<TerritoryUnitCount> response =
                restClient
                        .get()
                        .uri(
                                builder ->
                                        builder.path("/internal/combat/territories/unit-counts")
                                                .queryParam("territoryIds", territoryIds)
                                                .build())
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {});
        return response != null ? response : List.of();
    }

    public record UserSummary(long vaultGp, Long islandId, int islandLevel) {}

    public record TerritoryUnitCount(Long territoryId, long unitCount) {}
}
