package com.territorial.map.client;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * combat-service GP 자원 계약. map은 영토 저장소 조회·수입 적립·토지세 수금만 위임한다. GP·유닛·성은 combat-service가 소유(위치별 GP
 * 원칙).
 */
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

    public TerritoryStorageView getTerritoryStorage(Long territoryId) {
        return restClient
                .get()
                .uri("/internal/combat/territories/{territoryId}/storage", territoryId)
                .retrieve()
                .body(TerritoryStorageView.class);
    }

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

    public CreditIncomeResponse creditIncome(Long territoryId, int amount, String commandKey) {
        return restClient
                .post()
                .uri("/internal/combat/territories/{territoryId}/income-credits", territoryId)
                .body(new CreditIncomeRequest(amount, commandKey))
                .retrieve()
                .body(CreditIncomeResponse.class);
    }

    public record BuildingView(Long buildingId, String name, int level, int hp, int maxHp) {}

    public record TerritoryStorageView(
            List<BuildingView> buildings, int storedGp, int storageCapacity) {}

    public record CreditIncomeResponse(int creditedGp, int storedGp, int storageCapacity) {}

    private record ChargeTaxRequest(
            Long userId, int amount, List<Long> territoryIds, String commandKey) {}

    private record ChargeTaxResponse(boolean paid) {}

    private record CreditIncomeRequest(int amount, String commandKey) {}
}
