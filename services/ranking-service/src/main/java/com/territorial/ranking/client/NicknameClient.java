package com.territorial.ranking.client;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** user-service 닉네임 배치 조회. 랭킹 응답에 표시용 닉네임을 붙인다(신원은 user-service 소유). */
@Component
public class NicknameClient {

    private final RestClient restClient;

    public NicknameClient(
            RestClient.Builder builder,
            @Value("${user-service.base-url}") String baseUrl,
            @Value("${internal-api.secret}") String internalApiSecret) {
        this.restClient =
                builder.baseUrl(baseUrl)
                        .defaultHeader("X-Internal-Service-Token", internalApiSecret)
                        .build();
    }

    public Map<Long, String> getNicknames(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        List<UserNickname> response =
                restClient
                        .post()
                        .uri("/internal/users/nicknames")
                        .body(userIds)
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {});
        if (response == null) {
            return Map.of();
        }
        return response.stream()
                .collect(Collectors.toMap(UserNickname::userId, UserNickname::nickname));
    }

    public record UserNickname(Long userId, String nickname) {}
}
