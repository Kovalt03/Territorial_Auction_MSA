package com.territorial.user.client;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** season-service 조회 위임 — 프로필 합성에 쓰는 활성 시즌패스 요약만 읽는다. */
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

    public Optional<UserPassSummary> getUserPassSummary(Long userId) {
        UserPassSummary summary =
                restClient
                        .get()
                        .uri(ROOT + "/users/{userId}/pass-summary", userId)
                        .retrieve()
                        .body(UserPassSummary.class);
        return Optional.ofNullable(summary);
    }

    public record UserPassSummary(LocalDateTime expiresAt, int extraBuilders) {}
}
