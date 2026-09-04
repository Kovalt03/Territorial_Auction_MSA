package com.territorial.map.client;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** season-service 조회 계약. map은 활성 시즌(점유 만료 위임)·토지세 면제 보너스를 위임 조회한다. */
@Component
public class SeasonQueryClient {

    private static final String ROOT = "/internal/seasons";
    private final RestClient restClient;

    public SeasonQueryClient(
            RestClient.Builder builder,
            @Value("${season-service.base-url}") String baseUrl,
            @Value("${internal-api.secret}") String internalApiSecret) {
        this.restClient =
                builder.baseUrl(baseUrl)
                        .defaultHeader("X-Internal-Service-Token", internalApiSecret)
                        .build();
    }

    public Optional<ActiveSeason> getActiveSeason() {
        ActiveSeason season =
                restClient.get().uri(ROOT + "/active").retrieve().body(ActiveSeason.class);
        return Optional.ofNullable(season);
    }

    /** 토지세 면제 보너스(만료 필터 반영, 없으면 0). */
    public int getTaxExemptBonus(Long userId) {
        SeasonPassBenefitView view =
                restClient
                        .get()
                        .uri(ROOT + "/users/{userId}/pass-benefit", userId)
                        .retrieve()
                        .body(SeasonPassBenefitView.class);
        return view != null ? view.taxExemptBonus() : 0;
    }

    public record ActiveSeason(
            Long seasonId, Integer seasonNumber, LocalDateTime startedAt, LocalDateTime endedAt) {}

    private record SeasonPassBenefitView(int taxExemptBonus) {}
}
