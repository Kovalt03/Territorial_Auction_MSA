package com.territorial.auction.domain.user.client;

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
    public OAuthProvisionResult provisionOAuth(String username, String email, String nickname) {
        return restClient
                .post()
                .uri("/internal/users/provision-oauth")
                .body(new ProvisionOAuthRequest(username, email, nickname))
                .retrieve()
                .body(OAuthProvisionResult.class);
    }

    private record ProvisionOAuthRequest(String username, String email, String nickname) {}
}
