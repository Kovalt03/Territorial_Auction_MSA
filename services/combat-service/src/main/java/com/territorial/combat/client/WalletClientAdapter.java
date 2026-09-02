package com.territorial.combat.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.combat.domain.building.port.WalletPort;
import com.territorial.combat.global.exception.ErrorCode;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class WalletClientAdapter implements WalletPort {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public WalletClientAdapter(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            @Value("${user-service.base-url}") String baseUrl,
            @Value("${internal-api.secret}") String internalApiSecret) {
        this.restClient =
                builder.baseUrl(baseUrl)
                        .defaultHeader("X-Internal-Service-Token", internalApiSecret)
                        .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public WalletSnapshot spend(Long userId, int amount, String commandKey) {
        WalletResponse response =
                restClient
                        .post()
                        .uri("/internal/wallets/spend")
                        .body(new SpendRequest(userId, amount, commandKey))
                        .retrieve()
                        .onStatus(status -> status.value() == 404, this::mapNotFound)
                        .onStatus(status -> status.value() == 409, this::mapConflict)
                        .body(WalletResponse.class);
        return new WalletSnapshot(response != null ? response.availableAp() : 0);
    }

    private void mapNotFound(
            org.springframework.http.HttpRequest request,
            org.springframework.http.client.ClientHttpResponse response) {
        throw new CustomException(ErrorCode.USER_NOT_FOUND);
    }

    private void mapConflict(
            org.springframework.http.HttpRequest request,
            org.springframework.http.client.ClientHttpResponse response)
            throws IOException {
        if (hasMessage(response.getStatusCode(), response, "동일한 지갑 명령 키")) {
            throw new CustomException(ErrorCode.WALLET_COMMAND_CONFLICT);
        }
        throw new CustomException(ErrorCode.INSUFFICIENT_AP);
    }

    private boolean hasMessage(
            HttpStatusCode status,
            org.springframework.http.client.ClientHttpResponse response,
            String expected)
            throws IOException {
        JsonNode error = objectMapper.readTree(response.getBody());
        return status.value() == 409 && error.path("message").asText("").contains(expected);
    }

    private record SpendRequest(Long userId, int amount, String commandKey) {}

    private record WalletResponse(int availableAp, int lockedAp) {}
}
