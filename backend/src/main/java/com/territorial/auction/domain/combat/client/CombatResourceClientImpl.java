package com.territorial.auction.domain.combat.client;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class CombatResourceClientImpl implements CombatResourceClient {

    private final RestClient restClient;

    public CombatResourceClientImpl(
            RestClient.Builder builder,
            @Value("${combat-service.base-url}") String baseUrl,
            @Value("${internal-api.secret}") String internalApiSecret) {
        this.restClient =
                builder.baseUrl(baseUrl)
                        .defaultHeader("X-Internal-Service-Token", internalApiSecret)
                        .build();
    }

    @Override
    public UserSummary getUserSummary(Long userId) {
        return restClient
                .get()
                .uri("/internal/combat/users/{userId}/summary", userId)
                .retrieve()
                .body(UserSummary.class);
    }

    @Override
    public List<TerritoryUnitCount> getTerritoryUnitCounts(List<Long> territoryIds) {
        if (territoryIds.isEmpty()) {
            return List.of();
        }
        List<TerritoryUnitCount> response =
                restClient
                        .get()
                        .uri(
                                builder ->
                                        builder.path("/internal/combat/territories/unit-counts")
                                                .queryParam("territoryIds", territoryIds)
                                                .build())
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {});
        return response != null ? response : List.of();
    }

    @Override
    public TerritoryStorageView getTerritoryStorage(Long territoryId) {
        return restClient
                .get()
                .uri("/internal/combat/territories/{territoryId}/storage", territoryId)
                .retrieve()
                .body(TerritoryStorageView.class);
    }

    @Override
    public void creditGp(Long userId, int amount, String commandKey) {
        restClient
                .post()
                .uri("/internal/combat/resources/gp-credits")
                .body(new CreditGpRequest(userId, amount, commandKey))
                .retrieve()
                .toBodilessEntity();
    }

    @Override
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

    @Override
    public boolean chargeTax(Long userId, int amount, List<Long> territoryIds, String commandKey) {
        ChargeTaxResponse response =
                restClient
                        .post()
                        .uri("/internal/combat/resources/tax-charges")
                        .body(new ChargeTaxRequest(userId, amount, territoryIds, commandKey))
                        .retrieve()
                        .body(ChargeTaxResponse.class);
        return response != null && response.paid();
    }

    @Override
    public CreditIncomeResponse creditIncome(Long territoryId, int amount, String commandKey) {
        return restClient
                .post()
                .uri("/internal/combat/territories/{territoryId}/income-credits", territoryId)
                .body(new CreditIncomeRequest(amount, commandKey))
                .retrieve()
                .body(CreditIncomeResponse.class);
    }

    private record CreditGpRequest(Long userId, int amount, String commandKey) {}

    private record CreditAttackTokensRequest(
            Long userId, int normalCount, int precisionCount, String commandKey) {}

    private record ChargeTaxRequest(
            Long userId, int amount, List<Long> territoryIds, String commandKey) {}

    private record ChargeTaxResponse(boolean paid) {}

    private record CreditIncomeRequest(int amount, String commandKey) {}
}
