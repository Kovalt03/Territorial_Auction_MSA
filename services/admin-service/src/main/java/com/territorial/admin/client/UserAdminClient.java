package com.territorial.admin.client;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * user-service 유저 조회 위임(관리 콘솔용). 유저 신원은 user-service 소유라 admin은 표시·검증용으로만 조회한다. 상태 변경·생성은 {@link
 * UserProvisioningClient}(기존 계약)로 위임한다.
 */
@Component
public class UserAdminClient {

    private static final String ROOT = "/internal/users";
    private final RestClient restClient;

    public UserAdminClient(
            RestClient.Builder builder,
            @Value("${user-service.base-url}") String baseUrl,
            @Value("${internal-api.secret}") String internalApiSecret) {
        this.restClient =
                builder.baseUrl(baseUrl)
                        .defaultHeader("X-Internal-Service-Token", internalApiSecret)
                        .build();
    }

    public UserPage search(String status, String keyword, int page, int size) {
        return restClient
                .get()
                .uri(
                        builder -> {
                            var b =
                                    builder.path(ROOT)
                                            .queryParam("page", page)
                                            .queryParam("size", size);
                            if (status != null && !status.isBlank()) b.queryParam("status", status);
                            if (keyword != null && !keyword.isBlank())
                                b.queryParam("keyword", keyword);
                            return b.build();
                        })
                .retrieve()
                .body(UserPage.class);
    }

    public UserView get(Long userId) {
        return restClient.get().uri(ROOT + "/{id}", userId).retrieve().body(UserView.class);
    }

    public boolean exists(Long userId) {
        Boolean r =
                restClient.get().uri(ROOT + "/{id}/exists", userId).retrieve().body(Boolean.class);
        return Boolean.TRUE.equals(r);
    }

    public List<UserView> findAllByIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        List<UserView> r =
                restClient
                        .post()
                        .uri(ROOT + "/batch")
                        .body(new UserIds(userIds))
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {});
        return r != null ? r : List.of();
    }

    public UserCounts counts() {
        return restClient.get().uri(ROOT + "/counts").retrieve().body(UserCounts.class);
    }

    public record UserView(
            Long userId,
            String username,
            String nickname,
            String email,
            String status,
            String role,
            LocalDateTime createdAt) {

        public boolean isAdmin() {
            return "ADMIN".equals(role);
        }
    }

    public record UserPage(List<UserView> content, long totalElements, int page, int size) {}

    public record UserCounts(long total, long active, long suspended) {}

    private record UserIds(List<Long> userIds) {}
}
