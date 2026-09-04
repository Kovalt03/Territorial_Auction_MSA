package com.territorial.admin.client;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** 모놀리식 admin → item-service 아이템 관리 위임(목록·정책수정·CS 지급). 아이템 소유는 item-service. */
@Component
public class ItemAdminClient {

    private final RestClient restClient;

    public ItemAdminClient(
            RestClient.Builder builder,
            @Value("${item-service.base-url}") String baseUrl,
            @Value("${internal-api.secret}") String internalApiSecret) {
        this.restClient =
                builder.baseUrl(baseUrl)
                        .defaultHeader("X-Internal-Service-Token", internalApiSecret)
                        .build();
    }

    public List<ItemView> listItems() {
        List<ItemView> items =
                restClient
                        .get()
                        .uri("/internal/items")
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {});
        return items != null ? items : List.of();
    }

    public ItemView updatePolicy(Long itemId, Integer costAp, Integer costGp, Integer dailyLimit) {
        return restClient
                .patch()
                .uri("/internal/items/{itemId}/policy", itemId)
                .body(new UpdatePolicyRequest(costAp, costGp, dailyLimit))
                .retrieve()
                .body(ItemView.class);
    }

    public GrantResult grantById(Long userId, Long itemId, int quantity) {
        return restClient
                .post()
                .uri("/internal/items/grants")
                .body(new GrantByIdRequest(userId, itemId, quantity))
                .retrieve()
                .body(GrantResult.class);
    }

    public record ItemView(
            Long itemId,
            String name,
            String itemType,
            String description,
            Integer costAp,
            Integer costGp,
            Integer dailyLimit,
            Integer gpReward,
            String iconUrl) {}

    public record GrantResult(Long itemId, String itemName, int totalOwned) {}

    private record UpdatePolicyRequest(Integer costAp, Integer costGp, Integer dailyLimit) {}

    private record GrantByIdRequest(Long userId, Long itemId, int quantity) {}
}
