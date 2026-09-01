package com.territorial.auction.domain.user.client;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class WalletClientImpl implements WalletClient {

    private final RestClient restClient;

    public WalletClientImpl(
            RestClient.Builder builder,
            @Value("${user-service.base-url}") String baseUrl,
            @Value("${internal-api.secret}") String internalApiSecret) {
        this.restClient =
                builder.baseUrl(baseUrl)
                        .defaultHeader("X-Internal-Service-Token", internalApiSecret)
                        .build();
    }

    @Override
    public WalletSnapshot spend(Long userId, int amount, String commandKey) {
        return post("/internal/wallets/spend", new SpendRequest(userId, amount, commandKey));
    }

    @Override
    public WalletSnapshot credit(Long userId, int amount, String commandKey) {
        return post("/internal/wallets/credit", new CreditRequest(userId, amount, commandKey));
    }

    @Override
    public WalletSnapshot adjust(Long userId, int delta, String commandKey) {
        return post("/internal/wallets/adjust", new AdjustRequest(userId, delta, commandKey));
    }

    @Override
    public WalletSnapshot getWallet(Long userId) {
        return restClient
                .get()
                .uri("/internal/wallets/{id}", userId)
                .retrieve()
                .onStatus(
                        status -> status.value() == 404,
                        (req, res) -> {
                            throw new CustomException(ErrorCode.USER_NOT_FOUND);
                        })
                .body(WalletSnapshot.class);
    }

    @Override
    public long sumAvailableAp() {
        Long sum =
                restClient.get().uri("/internal/wallets/sum-available").retrieve().body(Long.class);
        return sum != null ? sum : 0L;
    }

    private WalletSnapshot post(String uri, Object body) {
        return restClient
                .post()
                .uri(uri)
                .body(body)
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

    private record SpendRequest(Long userId, int amount, String commandKey) {}

    private record CreditRequest(Long userId, int amount, String commandKey) {}

    private record AdjustRequest(Long userId, int delta, String commandKey) {}
}
