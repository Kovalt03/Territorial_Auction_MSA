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
            RestClient.Builder builder, @Value("${monolith.base-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
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
        // 409(이미 성 존재)는 모놀리식이 idempotent 처리(스킵)하므로 매핑 불필요
    }

    private record InitialCastleRequest(Long territoryId) {}
}
