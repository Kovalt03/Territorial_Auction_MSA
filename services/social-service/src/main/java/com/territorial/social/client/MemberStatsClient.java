package com.territorial.social.client;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** 멤버 표시 통계(영토·트로피)를 모놀리식 /internal/members/stats에서 배치 조회. map·season은 모놀리식 소유. */
@Component
public class MemberStatsClient {

    private final RestClient restClient;

    public MemberStatsClient(
            RestClient.Builder builder, @Value("${monolith.base-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    public Map<Long, MemberStat> fetch(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<MemberStat> stats =
                restClient
                        .post()
                        .uri("/internal/members/stats")
                        .body(new StatsRequest(userIds))
                        .retrieve()
                        .body(new ParameterizedTypeReference<List<MemberStat>>() {});
        return stats == null
                ? Map.of()
                : stats.stream().collect(Collectors.toMap(MemberStat::userId, s -> s));
    }

    public record MemberStat(Long userId, long territoryCount, long trophyScore) {}

    private record StatsRequest(List<Long> userIds) {}
}
