package com.territorial.season.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** item → combat-service 자원 지급(GP 금고 적립·공격권). GP/공격권 소유는 combat-service. */
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

    public void creditGp(Long userId, int amount, String commandKey) {
        restClient
                .post()
                .uri("/internal/combat/resources/gp-credits")
                .body(new CreditGpRequest(userId, amount, commandKey))
                .retrieve()
                .toBodilessEntity();
    }

    public AttackTokenBalance creditAttackTokens(
            Long userId, int normalCount, int precisionCount, String commandKey) {
        return restClient
                .post()
                .uri("/internal/combat/resources/attack-token-credits")
                .body(
                        new CreditAttackTokensRequest(
                                userId, normalCount, precisionCount, commandKey))
                .retrieve()
                .body(AttackTokenBalance.class);
    }

    public record AttackTokenBalance(int normalCount, int precisionCount) {}

    private record CreditGpRequest(Long userId, int amount, String commandKey) {}

    private record CreditAttackTokensRequest(
            Long userId, int normalCount, int precisionCount, String commandKey) {}
}
