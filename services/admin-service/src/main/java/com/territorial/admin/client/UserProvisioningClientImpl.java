package com.territorial.admin.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class UserProvisioningClientImpl implements UserProvisioningClient {

    private final RestClient restClient;

    public UserProvisioningClientImpl(
            RestClient.Builder builder,
            @Value("${user-service.base-url}") String baseUrl,
            @Value("${internal-api.secret}") String internalApiSecret) {
        this.restClient =
                builder.baseUrl(baseUrl)
                        .defaultHeader("X-Internal-Service-Token", internalApiSecret)
                        .build();
    }

    @Override
    public void changeStatus(Long userId, String status) {
        restClient
                .post()
                .uri("/internal/users/{userId}/status", userId)
                .body(new ChangeStatusRequest(status))
                .retrieve()
                .toBodilessEntity();
    }

    private record ChangeStatusRequest(String status) {}
}
