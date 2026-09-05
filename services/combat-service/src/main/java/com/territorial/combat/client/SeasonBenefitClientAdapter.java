package com.territorial.combat.client;

import com.territorial.combat.domain.building.port.SeasonBenefitPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class SeasonBenefitClientAdapter implements SeasonBenefitPort {

    private final RestClient restClient;

    public SeasonBenefitClientAdapter(
            RestClient.Builder builder,
            @Value("${season-service.base-url}") String baseUrl,
            @Value("${internal-api.secret}") String internalApiSecret) {
        this.restClient =
                builder.baseUrl(baseUrl)
                        .defaultHeader("X-Internal-Service-Token", internalApiSecret)
                        .build();
    }

    @Override
    public SeasonBenefit findActiveBenefit(Long userId) {
        SeasonBenefitResponse response =
                restClient
                        .get()
                        .uri("/internal/seasons/users/{userId}/combat-benefit", userId)
                        .retrieve()
                        .body(SeasonBenefitResponse.class);
        return response == null
                ? SeasonBenefit.none()
                : new SeasonBenefit(response.buildTimeReductionPct(), response.extraBuilders());
    }

    private record SeasonBenefitResponse(int buildTimeReductionPct, int extraBuilders) {}
}
