package com.territorial.combat.client;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
public class TerritoryClient {

    private final RestClient restClient;

    public TerritoryClient(
            RestClient.Builder builder,
            @Value("${map-service.base-url}") String baseUrl,
            @Value("${internal-api.secret}") String internalApiSecret) {
        this.restClient =
                builder.baseUrl(baseUrl)
                        .defaultHeader("X-Internal-Service-Token", internalApiSecret)
                        .build();
    }

    public Optional<TerritoryCombatContextResponse> findById(Long territoryId) {
        try {
            return Optional.ofNullable(
                    restClient
                            .get()
                            .uri("/internal/territories/{id}/combat-context", territoryId)
                            .retrieve()
                            .body(TerritoryCombatContextResponse.class));
        } catch (HttpClientErrorException.NotFound exception) {
            return Optional.empty();
        }
    }

    public List<TerritoryCombatContextResponse> findOwnedByUserId(Long userId) {
        List<TerritoryCombatContextResponse> response =
                restClient
                        .get()
                        .uri("/internal/territories/owners/{userId}/combat-contexts", userId)
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {});
        return response != null ? response : List.of();
    }

    public record TerritoryCombatContextResponse(
            Long territoryId,
            Long ownerId,
            int coordX,
            int coordY,
            String status,
            LocalDateTime protectedUntil,
            String grade,
            int gridSize,
            int zone1Radius,
            int zone2Radius) {}
}
