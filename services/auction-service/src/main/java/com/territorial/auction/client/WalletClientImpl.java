package com.territorial.auction.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class WalletClientImpl implements WalletClient {

    private static final String COMMAND_CONFLICT_MESSAGE = "동일한 지갑 명령 키에 다른 요청이 전달되었습니다.";
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public WalletClientImpl(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            @Value("${user-service.base-url}") String baseUrl,
            @Value("${internal-api.secret}") String internalApiSecret) {
        this.objectMapper = objectMapper;
        this.restClient =
                builder.baseUrl(baseUrl)
                        .defaultHeader("X-Internal-Service-Token", internalApiSecret)
                        .build();
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
                            throwWalletConflict(res);
                        })
                .body(BidEscrowResult.class);
    }

    @Override
    public void compensateBidEscrow(BidEscrowRequest request) {
        restClient
                .post()
                .uri("/internal/wallets/bid-escrow-compensate")
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
                            throwWalletConflict(res);
                        })
                .toBodilessEntity();
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
                            throwWalletConflict(res);
                        })
                .toBodilessEntity();
    }

    @Override
    public void refundLocked(Long bidderId, int amount, Long auctionId) {
        restClient
                .post()
                .uri("/internal/wallets/refund-locked")
                .body(new RefundLockedRequest(bidderId, amount, auctionId))
                .retrieve()
                .onStatus(
                        status -> status.value() == 404,
                        (req, res) -> {
                            throw new CustomException(ErrorCode.USER_NOT_FOUND);
                        })
                .onStatus(
                        status -> status.value() == 409,
                        (req, res) -> {
                            throwWalletConflict(res);
                        })
                .toBodilessEntity();
    }

    private void throwWalletConflict(ClientHttpResponse response) throws IOException {
        String message = objectMapper.readTree(response.getBody()).path("message").asText();
        if (COMMAND_CONFLICT_MESSAGE.equals(message)) {
            throw new CustomException(ErrorCode.WALLET_COMMAND_CONFLICT);
        }
        throw new CustomException(ErrorCode.INSUFFICIENT_AP);
    }

    private record ConsumeLockedRequest(Long winnerId, int finalPrice, Long auctionId) {}

    private record RefundLockedRequest(Long bidderId, int amount, Long auctionId) {}
}
