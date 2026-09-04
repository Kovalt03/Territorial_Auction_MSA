package com.territorial.admin.domain.admin.service;

import com.territorial.admin.client.MapAdminClient;
import com.territorial.admin.domain.admin.dto.AdminBulkResultResponse;
import com.territorial.admin.domain.admin.dto.AdminContinentCompositionResponse;
import com.territorial.admin.domain.admin.dto.AdminContinentCompositionResponse.ContinentComposition;
import com.territorial.admin.domain.admin.dto.AdminGradeDistributionRequest;
import com.territorial.admin.domain.admin.dto.AdminToggleAuctionRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 관리자 대륙 구성 관리. 구성 집계·등급 재분배·대륙 경매 토글을 map-service(/internal/admin)로 위임하고, 여기서는 감사 로그를 남긴다. */
@Service
@RequiredArgsConstructor
public class AdminContinentService {

    private final MapAdminClient mapAdminClient;
    private final AdminAuditLogger adminAuditLogger;

    public AdminContinentCompositionResponse getCompositions() {
        return new AdminContinentCompositionResponse(
                mapAdminClient.getCompositions().continents().stream()
                        .map(AdminContinentService::toComposition)
                        .toList());
    }

    public ContinentComposition applyGradeDistribution(
            Long adminUserId, Long continentId, AdminGradeDistributionRequest request) {
        MapAdminClient.GradeDistributionResult result =
                mapAdminClient.applyGradeDistribution(continentId, request.distribution());
        adminAuditLogger.record(
                adminUserId,
                "CONTINENT_GRADE_DISTRIBUTION",
                "CONTINENT",
                continentId,
                Map.of(
                        "before",
                        result.before(),
                        "after",
                        request.distribution(),
                        "reason",
                        request.reason() != null ? request.reason() : ""));
        return toComposition(result.composition());
    }

    // 대륙(행성) 전체 영토의 경매 활성/비활성을 한 번에 변경한다.
    public AdminBulkResultResponse changeContinentAuction(
            Long adminUserId, Long continentId, AdminToggleAuctionRequest request) {
        int affected = mapAdminClient.changeContinentAuction(continentId, request.enabled());
        adminAuditLogger.record(
                adminUserId,
                "CONTINENT_AUCTION_TOGGLE",
                "CONTINENT",
                continentId,
                Map.of(
                        "enabled",
                        request.enabled(),
                        "reason",
                        request.reason() != null ? request.reason() : ""));
        return new AdminBulkResultResponse(affected);
    }

    private static ContinentComposition toComposition(MapAdminClient.ContinentComposition c) {
        return new ContinentComposition(
                c.continentId(),
                c.name(),
                c.minTrophyRequired(),
                c.totalTerritories(),
                c.gradeBreakdown(),
                c.biddingCount(),
                c.occupiedCount(),
                c.idleCount());
    }
}
