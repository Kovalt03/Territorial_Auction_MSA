package com.territorial.auction.domain.admin.service;

import com.territorial.auction.domain.admin.dto.AdminBulkForceStartRequest;
import com.territorial.auction.domain.admin.dto.AdminBulkGradeRequest;
import com.territorial.auction.domain.admin.dto.AdminBulkResultResponse;
import com.territorial.auction.domain.admin.dto.AdminBulkTerritoryAuctionRequest;
import com.territorial.auction.domain.admin.dto.AdminChangeGradeRequest;
import com.territorial.auction.domain.admin.dto.AdminTerritoryListResponse;
import com.territorial.auction.domain.admin.dto.AdminTerritoryResponse;
import com.territorial.auction.domain.admin.dto.AdminToggleAuctionRequest;
import com.territorial.auction.global.client.MapAdminClient;
import com.territorial.auction.global.client.MapAdminClient.ChangeResult;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 관리자 영토 관리. 실제 영토 변경은 map-service(/internal/admin)로 위임하고, 여기서는 감사 로그(before/after)를 남긴다. 영토 그리드 캐시
 * 무효화는 map-service가 담당한다.
 */
@Service
@RequiredArgsConstructor
public class AdminTerritoryService {

    private final MapAdminClient mapAdminClient;
    private final AdminAuditLogger adminAuditLogger;

    public AdminTerritoryListResponse getTerritories(Long continentId) {
        return new AdminTerritoryListResponse(
                mapAdminClient.getContinentTerritories(continentId).stream()
                        .map(AdminTerritoryResponse::from)
                        .toList());
    }

    public AdminTerritoryResponse changeGrade(
            Long adminUserId, Long territoryId, AdminChangeGradeRequest request) {
        ChangeResult result = mapAdminClient.changeGrade(territoryId, request.grade());
        adminAuditLogger.record(
                adminUserId,
                "TERRITORY_GRADE_CHANGE",
                "TERRITORY",
                territoryId,
                Map.of(
                        "before", result.beforeGrade(),
                        "after", request.grade(),
                        "reason", nullSafe(request.reason())));
        return AdminTerritoryResponse.from(result.territory());
    }

    public AdminTerritoryResponse changeAuctionEnabled(
            Long adminUserId, Long territoryId, AdminToggleAuctionRequest request) {
        ChangeResult result = mapAdminClient.changeAuctionEnabled(territoryId, request.enabled());
        adminAuditLogger.record(
                adminUserId,
                "TERRITORY_AUCTION_TOGGLE",
                "TERRITORY",
                territoryId,
                Map.of(
                        "before", result.beforeAuctionEnabled(),
                        "after", request.enabled(),
                        "reason", nullSafe(request.reason())));
        return AdminTerritoryResponse.from(result.territory());
    }

    public AdminTerritoryResponse forceStartAuction(Long adminUserId, Long territoryId) {
        ChangeResult result = mapAdminClient.forceStartAuction(territoryId);
        adminAuditLogger.record(
                adminUserId,
                "TERRITORY_AUCTION_FORCE_START",
                "TERRITORY",
                territoryId,
                Map.of("territoryId", territoryId));
        return AdminTerritoryResponse.from(result.territory());
    }

    public AdminBulkResultResponse bulkChangeGrade(
            Long adminUserId, AdminBulkGradeRequest request) {
        List<ChangeResult> results =
                mapAdminClient.bulkChangeGrade(request.grade(), request.territoryIds());
        for (ChangeResult result : results) {
            adminAuditLogger.record(
                    adminUserId,
                    "TERRITORY_GRADE_CHANGE_BULK",
                    "TERRITORY",
                    result.territory().territoryId(),
                    Map.of(
                            "before", result.beforeGrade(),
                            "after", request.grade(),
                            "reason", nullSafe(request.reason())));
        }
        return new AdminBulkResultResponse(results.size());
    }

    public AdminBulkResultResponse bulkChangeAuction(
            Long adminUserId, AdminBulkTerritoryAuctionRequest request) {
        List<ChangeResult> results =
                mapAdminClient.bulkChangeAuction(request.enabled(), request.territoryIds());
        for (ChangeResult result : results) {
            adminAuditLogger.record(
                    adminUserId,
                    "TERRITORY_AUCTION_TOGGLE_BULK",
                    "TERRITORY",
                    result.territory().territoryId(),
                    Map.of(
                            "before", result.beforeAuctionEnabled(),
                            "after", request.enabled(),
                            "reason", nullSafe(request.reason())));
        }
        return new AdminBulkResultResponse(results.size());
    }

    public AdminBulkResultResponse bulkForceStart(
            Long adminUserId, AdminBulkForceStartRequest request) {
        List<ChangeResult> results = mapAdminClient.bulkForceStart(request.territoryIds());
        for (ChangeResult result : results) {
            adminAuditLogger.record(
                    adminUserId,
                    "TERRITORY_AUCTION_FORCE_START_BULK",
                    "TERRITORY",
                    result.territory().territoryId(),
                    Map.of("territoryId", result.territory().territoryId()));
        }
        return new AdminBulkResultResponse(results.size());
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }
}
