package com.territorial.auction.client;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class TerritoryClientImpl implements TerritoryClient {

    private final RestClient restClient;

    public TerritoryClientImpl(
            RestClient.Builder builder, @Value("${monolith.base-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    @Override
    public void occupy(
            Long territoryId,
            Long winnerId,
            LocalDateTime occupiedUntil,
            LocalDateTime protectedUntil) {
        restClient
                .post()
                .uri("/internal/territories/{id}/occupy", territoryId)
                .body(new OccupyRequest(winnerId, occupiedUntil, protectedUntil))
                .retrieve()
                .onStatus(
                        status -> status.value() == 404,
                        (req, res) -> {
                            throw new CustomException(ErrorCode.TERRITORY_NOT_FOUND);
                        })
                .toBodilessEntity();
    }

    @Override
    public void release(Long territoryId, LocalDateTime nextAuctionAt) {
        restClient
                .post()
                .uri("/internal/territories/{id}/release", territoryId)
                .body(new ReleaseRequest(nextAuctionAt))
                .retrieve()
                .onStatus(
                        status -> status.value() == 404,
                        (req, res) -> {
                            throw new CustomException(ErrorCode.TERRITORY_NOT_FOUND);
                        })
                .toBodilessEntity();
    }

    // 요청 바디 — 모놀리식 Part B 엔드포인트 계약과 맞출 것
    private record OccupyRequest(
            Long winnerId, LocalDateTime occupiedUntil, LocalDateTime protectedUntil) {}

    private record ReleaseRequest(LocalDateTime nextAuctionAt) {}
}
