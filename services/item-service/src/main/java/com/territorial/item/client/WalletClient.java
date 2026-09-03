package com.territorial.item.client;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.item.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** item → user-service 지갑(AP) 소비. AP 소유는 user-service. commandKey는 멱등·보상 짝맞춤용 안정 키. */
@Component
public class WalletClient {

    private final RestClient restClient;

    public WalletClient(
            RestClient.Builder builder,
            @Value("${user-service.base-url}") String baseUrl,
            @Value("${internal-api.secret}") String internalApiSecret) {
        this.restClient =
                builder.baseUrl(baseUrl)
                        .defaultHeader("X-Internal-Service-Token", internalApiSecret)
                        .build();
    }

    public WalletSnapshot spend(Long userId, int amount, String commandKey) {
        return restClient
                .post()
                .uri("/internal/wallets/spend")
                .body(new SpendRequest(userId, amount, commandKey))
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
                .body(WalletSnapshot.class);
    }

    public record WalletSnapshot(int availableAp, int lockedAp) {}

    private record SpendRequest(Long userId, int amount, String commandKey) {}
}
