package com.territorial.auction.domain.season.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** season → item-service 보상 아이템 지급. 아이템 소유는 item-service. */
@Component
public class ItemGrantClient {

    private final RestClient restClient;

    public ItemGrantClient(
            RestClient.Builder builder,
            @Value("${item-service.base-url}") String baseUrl,
            @Value("${internal-api.secret}") String internalApiSecret) {
        this.restClient =
                builder.baseUrl(baseUrl)
                        .defaultHeader("X-Internal-Service-Token", internalApiSecret)
                        .build();
    }

    public void grantByType(Long userId, String itemType, int quantity) {
        restClient
                .post()
                .uri("/internal/items/grants/by-type")
                .body(new GrantByTypeRequest(userId, itemType, quantity))
                .retrieve()
                .toBodilessEntity();
    }

    private record GrantByTypeRequest(Long userId, String itemType, int quantity) {}
}
