package com.territorial.auction.client;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class WalletClientImpl implements WalletClient {

    private final RestClient restClient;

    public WalletClientImpl(
            RestClient.Builder builder, @Value("${monolith.base-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    @Override
    public BidEscrowResult bidEscrow(BidEscrowRequest request) {
        return restClient
                .post()
                .uri("/internal/wallets/bid-escrow")
                .body(request)
                .retrieve()
                .onStatus(
                        status -> status.value() == 404,
                        (req, res) -> {
                            throw new CustomException(ErrorCode.USER_NOT_FOUND);
                        })
                .onStatus(
                        status -> status.value() == 409,
                        (req, res) -> {
                            throw new CustomException(ErrorCode.INSUFFICIENT_AP);
                        })
                .body(BidEscrowResult.class);
    }

    @Override
    public void consumeLocked(Long winnerId, int finalPrice, Long auctionId) {
        restClient
                .post()
                .uri("/internal/wallets/consume-locked")
                .body(new ConsumeLockedRequest(winnerId, finalPrice, auctionId))
                .retrieve()
                .onStatus(
                        status -> status.value() == 404,
                        (req, res) -> {
                            throw new CustomException(ErrorCode.USER_NOT_FOUND);
                        })
                .onStatus(
                        status -> status.value() == 409,
                        (req, res) -> {
                            throw new CustomException(ErrorCode.INSUFFICIENT_AP);
                        })
                .toBodilessEntity();
    }

    private record ConsumeLockedRequest(Long winnerId, int finalPrice, Long auctionId) {}
}
