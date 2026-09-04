package com.territorial.item.client;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.item.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** item → 모놀리식(map) 영토 소유권 조회. 무적권 사용 시 대상 영토가 본인 소유인지 검증한다. */
@Component
public class TerritoryOwnershipClient {

    private final RestClient restClient;

    public TerritoryOwnershipClient(
            RestClient.Builder builder,
            @Value("${map-service.base-url}") String baseUrl,
            @Value("${internal-api.secret}") String internalApiSecret) {
        this.restClient =
                builder.baseUrl(baseUrl)
                        .defaultHeader("X-Internal-Service-Token", internalApiSecret)
                        .build();
    }

    /** 대상 영토의 소유자 userId. 소유자가 없으면 null. 영토 미존재 시 TERRITORY_NOT_FOUND. */
    public Long getOwnerId(Long territoryId) {
        TerritoryCombatContext context =
                restClient
                        .get()
                        .uri("/internal/territories/{id}/combat-context", territoryId)
                        .retrieve()
                        .onStatus(
                                status -> status.value() == 404,
                                (req, res) -> {
                                    throw new CustomException(ErrorCode.TERRITORY_NOT_FOUND);
                                })
                        .body(TerritoryCombatContext.class);
        return context != null ? context.ownerId() : null;
    }

    private record TerritoryCombatContext(Long territoryId, Long ownerId) {}
}
