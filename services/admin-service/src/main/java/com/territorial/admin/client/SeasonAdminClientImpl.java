package com.territorial.admin.client;

import com.territorial.admin.global.exception.ErrorCode;
import com.territorial.auction.global.exception.CustomException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class SeasonAdminClientImpl implements SeasonAdminClient {

    private static final String ROOT = "/internal/admin/seasons";
    private final RestClient restClient;

    public SeasonAdminClientImpl(
            RestClient.Builder builder,
            @Value("${season-service.base-url}") String baseUrl,
            @Value("${internal-api.secret}") String internalApiSecret) {
        this.restClient =
                builder.baseUrl(baseUrl)
                        .defaultHeader("X-Internal-Service-Token", internalApiSecret)
                        .defaultStatusHandler(
                                status -> status.is4xxClientError(),
                                SeasonAdminClientImpl::handleClientError)
                        .build();
    }

    @Override
    public List<SeasonView> getSeasons() {
        List<SeasonView> response =
                restClient.get().uri(ROOT).retrieve().body(new ParameterizedTypeReference<>() {});
        return response != null ? response : List.of();
    }

    @Override
    public SeasonView createSeason(LocalDateTime startedAt, LocalDateTime endedAt) {
        return restClient
                .post()
                .uri(ROOT)
                .body(new CreateSeasonRequest(startedAt, endedAt))
                .retrieve()
                .body(SeasonView.class);
    }

    @Override
    public SeasonView endSeason(Long seasonId) {
        return restClient
                .post()
                .uri(ROOT + "/{seasonId}/end", seasonId)
                .retrieve()
                .body(SeasonView.class);
    }

    @Override
    public List<SeasonPassView> getSeasonPasses() {
        List<SeasonPassView> response =
                restClient
                        .get()
                        .uri(ROOT + "/passes")
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {});
        return response != null ? response : List.of();
    }

    @Override
    public SeasonPassView updateSeasonPass(Long seasonPassId, UpdateSeasonPassCommand command) {
        return restClient
                .patch()
                .uri(ROOT + "/passes/{seasonPassId}", seasonPassId)
                .body(command)
                .retrieve()
                .body(SeasonPassView.class);
    }

    private record CreateSeasonRequest(LocalDateTime startedAt, LocalDateTime endedAt) {}

    private static void handleClientError(HttpRequest request, ClientHttpResponse response)
            throws IOException {
        String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
        for (ErrorCode errorCode : ErrorCode.values()) {
            if (body.contains(errorCode.getMessage())) {
                throw new CustomException(errorCode);
            }
        }
        throw new CustomException(ErrorCode.INVALID_INPUT);
    }
}
