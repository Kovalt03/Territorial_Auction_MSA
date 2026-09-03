package com.territorial.auction.global.client;

import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class SeasonQueryClientImpl implements SeasonQueryClient {

    private static final String ROOT = "/internal/seasons";
    private final RestClient restClient;

    public SeasonQueryClientImpl(
            RestClient.Builder builder,
            @Value("${season-service.base-url}") String baseUrl,
            @Value("${internal-api.secret}") String internalApiSecret) {
        this.restClient =
                builder.baseUrl(baseUrl)
                        .defaultHeader("X-Internal-Service-Token", internalApiSecret)
                        .build();
    }

    @Override
    public Optional<ActiveSeason> getActiveSeason() {
        ActiveSeason season =
                restClient.get().uri(ROOT + "/active").retrieve().body(ActiveSeason.class);
        return Optional.ofNullable(season);
    }

    @Override
    public int getTaxExemptBonus(Long userId) {
        SeasonPassBenefitView view =
                restClient
                        .get()
                        .uri(ROOT + "/users/{userId}/pass-benefit", userId)
                        .retrieve()
                        .body(SeasonPassBenefitView.class);
        return view != null ? view.taxExemptBonus() : 0;
    }

    @Override
    public Optional<UserPassSummary> getUserPassSummary(Long userId) {
        UserPassSummary summary =
                restClient
                        .get()
                        .uri(ROOT + "/users/{userId}/pass-summary", userId)
                        .retrieve()
                        .body(UserPassSummary.class);
        return Optional.ofNullable(summary);
    }

    private record SeasonPassBenefitView(int taxExemptBonus) {}
}
