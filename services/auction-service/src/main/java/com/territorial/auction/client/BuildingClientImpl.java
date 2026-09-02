package com.territorial.auction.client;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class BuildingClientImpl implements BuildingClient {

    private final RestClient restClient;

    public BuildingClientImpl(
            RestClient.Builder builder,
            @Value("${combat-service.base-url}") String baseUrl,
            @Value("${internal-api.secret}") String internalApiSecret) {
        this.restClient =
                builder.baseUrl(baseUrl)
                        .defaultHeader("X-Internal-Service-Token", internalApiSecret)
                        .build();
    }

    @Override
    public void createInitialCastle(Long territoryId) {
        restClient
                .post()
                .uri("/internal/buildings/initial-castle")
                .body(new InitialCastleRequest(territoryId))
                .retrieve()
                .onStatus(
                        status -> status.value() == 404,
                        (req, res) -> {
                            throw new CustomException(ErrorCode.BUILDING_TYPE_NOT_FOUND);
                        })
                .toBodilessEntity();
        // 이미 성이 존재하면 combat-service가 성공으로 간주한다.
    }

    private record InitialCastleRequest(Long territoryId) {}
}
