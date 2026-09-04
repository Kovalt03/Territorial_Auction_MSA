package com.territorial.auction.global.client;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * map-service 관리자 영토·대륙 관리 위임(공유 커널 map 추출). 모놀리식 admin 도메인이 감사 로그를 남기며 실제 변경은 이 계약으로 위임한다. 변경 응답은
 * 감사(before/after)용 변경 전 값을 포함한다.
 */
@Component
public class MapAdminClient {

    private static final String ROOT = "/internal/admin";
    private final RestClient restClient;

    public MapAdminClient(
            RestClient.Builder builder,
            @Value("${map-service.base-url}") String baseUrl,
            @Value("${internal-api.secret}") String internalApiSecret) {
        this.restClient =
                builder.baseUrl(baseUrl)
                        .defaultHeader("X-Internal-Service-Token", internalApiSecret)
                        .build();
    }

    public CompositionResponse getCompositions() {
        return restClient
                .get()
                .uri(ROOT + "/continents/compositions")
                .retrieve()
                .body(CompositionResponse.class);
    }

    public GradeDistributionResult applyGradeDistribution(
            Long continentId, Map<String, Integer> distribution) {
        return restClient
                .post()
                .uri(ROOT + "/continents/{id}/grade-distribution", continentId)
                .body(new GradeDistributionRequest(distribution))
                .retrieve()
                .body(GradeDistributionResult.class);
    }

    public int changeContinentAuction(Long continentId, boolean enabled) {
        AffectedResult result =
                restClient
                        .post()
                        .uri(ROOT + "/continents/{id}/auction-toggle", continentId)
                        .body(new ToggleRequest(enabled))
                        .retrieve()
                        .body(AffectedResult.class);
        return result != null ? result.affected() : 0;
    }

    public List<TerritoryView> getContinentTerritories(Long continentId) {
        List<TerritoryView> result =
                restClient
                        .get()
                        .uri(ROOT + "/continents/{id}/territories", continentId)
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {});
        return result != null ? result : List.of();
    }

    public ChangeResult changeGrade(Long territoryId, String grade) {
        return restClient
                .post()
                .uri(ROOT + "/territories/{id}/grade", territoryId)
                .body(new GradeRequest(grade))
                .retrieve()
                .body(ChangeResult.class);
    }

    public ChangeResult changeAuctionEnabled(Long territoryId, boolean enabled) {
        return restClient
                .post()
                .uri(ROOT + "/territories/{id}/auction-toggle", territoryId)
                .body(new ToggleRequest(enabled))
                .retrieve()
                .body(ChangeResult.class);
    }

    public ChangeResult forceStartAuction(Long territoryId) {
        return restClient
                .post()
                .uri(ROOT + "/territories/{id}/force-start", territoryId)
                .retrieve()
                .body(ChangeResult.class);
    }

    public List<ChangeResult> bulkChangeGrade(String grade, List<Long> territoryIds) {
        List<ChangeResult> result =
                restClient
                        .post()
                        .uri(ROOT + "/territories/grade-bulk")
                        .body(new BulkGradeRequest(grade, territoryIds))
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {});
        return result != null ? result : List.of();
    }

    public List<ChangeResult> bulkChangeAuction(boolean enabled, List<Long> territoryIds) {
        List<ChangeResult> result =
                restClient
                        .post()
                        .uri(ROOT + "/territories/auction-toggle-bulk")
                        .body(new BulkToggleRequest(enabled, territoryIds))
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {});
        return result != null ? result : List.of();
    }

    public List<ChangeResult> bulkForceStart(List<Long> territoryIds) {
        List<ChangeResult> result =
                restClient
                        .post()
                        .uri(ROOT + "/territories/force-start-bulk")
                        .body(new BulkIdsRequest(territoryIds))
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {});
        return result != null ? result : List.of();
    }

    public UserTerritoryPage getUserTerritories(Long userId, int page, int size) {
        return restClient
                .get()
                .uri(
                        builder ->
                                builder.path(ROOT + "/users/{userId}/territories")
                                        .queryParam("page", page)
                                        .queryParam("size", size)
                                        .build(userId))
                .retrieve()
                .body(UserTerritoryPage.class);
    }

    public StatusCounts getStatusCounts() {
        return restClient
                .get()
                .uri(ROOT + "/dashboard/territory-status-counts")
                .retrieve()
                .body(StatusCounts.class);
    }

    public record TerritoryView(
            Long territoryId,
            int coordX,
            int coordY,
            String grade,
            String status,
            String ownerNickname,
            boolean auctionEnabled) {}

    public record ChangeResult(
            String beforeGrade, Boolean beforeAuctionEnabled, TerritoryView territory) {}

    public record ContinentComposition(
            Long continentId,
            String name,
            Integer minTrophyRequired,
            long totalTerritories,
            Map<String, Long> gradeBreakdown,
            long biddingCount,
            long occupiedCount,
            long idleCount) {}

    public record CompositionResponse(List<ContinentComposition> continents) {}

    public record GradeDistributionResult(
            Map<String, Long> before, ContinentComposition composition) {}

    public record UserTerritoryView(
            Long territoryId,
            int coordX,
            int coordY,
            String continentName,
            String grade,
            String status,
            LocalDateTime occupiedUntil) {}

    public record UserTerritoryPage(List<UserTerritoryView> content, long totalElements) {}

    public record StatusCounts(long biddingCount, long occupiedCount, long idleCount) {}

    private record AffectedResult(int affected) {}

    private record GradeDistributionRequest(Map<String, Integer> distribution) {}

    private record ToggleRequest(boolean enabled) {}

    private record GradeRequest(String grade) {}

    private record BulkGradeRequest(String grade, List<Long> territoryIds) {}

    private record BulkToggleRequest(boolean enabled, List<Long> territoryIds) {}

    private record BulkIdsRequest(List<Long> territoryIds) {}
}
