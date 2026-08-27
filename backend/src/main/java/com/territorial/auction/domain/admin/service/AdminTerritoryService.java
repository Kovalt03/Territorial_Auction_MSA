package com.territorial.auction.domain.admin.service;

import com.territorial.auction.domain.admin.dto.AdminBulkForceStartRequest;
import com.territorial.auction.domain.admin.dto.AdminBulkGradeRequest;
import com.territorial.auction.domain.admin.dto.AdminBulkResultResponse;
import com.territorial.auction.domain.admin.dto.AdminBulkTerritoryAuctionRequest;
import com.territorial.auction.domain.admin.dto.AdminChangeGradeRequest;
import com.territorial.auction.domain.admin.dto.AdminTerritoryListResponse;
import com.territorial.auction.domain.admin.dto.AdminTerritoryResponse;
import com.territorial.auction.domain.admin.dto.AdminToggleAuctionRequest;
import com.territorial.auction.domain.map.entity.Territory;
import com.territorial.auction.domain.map.entity.TerritoryGrade;
import com.territorial.auction.domain.map.repository.ContinentRepository;
import com.territorial.auction.domain.map.repository.TerritoryGradeRepository;
import com.territorial.auction.domain.map.repository.TerritoryRepository;
import com.territorial.auction.domain.map.service.TerritoryAuctionReadyPublisher;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminTerritoryService {

    private final TerritoryRepository territoryRepository;
    private final TerritoryGradeRepository territoryGradeRepository;
    private final ContinentRepository continentRepository;
    private final TerritoryAuctionReadyPublisher territoryAuctionReadyPublisher;
    private final AdminAuditLogger adminAuditLogger;

    public AdminTerritoryListResponse getTerritories(Long continentId) {
        if (!continentRepository.existsById(continentId)) {
            throw new CustomException(ErrorCode.CONTINENT_NOT_FOUND);
        }
        return new AdminTerritoryListResponse(
                territoryRepository.findAllByContinentIdWithDetails(continentId).stream()
                        .map(AdminTerritoryResponse::from)
                        .toList());
    }

    @Transactional
    @CacheEvict(
            value = {"territory-grid", "territory-grid-etag"},
            allEntries = true)
    public AdminTerritoryResponse changeGrade(
            Long adminUserId, Long territoryId, AdminChangeGradeRequest request) {
        Territory territory =
                territoryRepository
                        .findById(territoryId)
                        .orElseThrow(() -> new CustomException(ErrorCode.TERRITORY_NOT_FOUND));
        validateNotOccupied(territory);
        TerritoryGrade grade =
                territoryGradeRepository
                        .findByGrade(request.grade())
                        .orElseThrow(
                                () -> new CustomException(ErrorCode.TERRITORY_GRADE_NOT_FOUND));

        String before = territory.getGrade().getGrade();
        territory.changeGrade(grade);

        adminAuditLogger.record(
                adminUserId,
                "TERRITORY_GRADE_CHANGE",
                "TERRITORY",
                territoryId,
                Map.of(
                        "before",
                        before,
                        "after",
                        request.grade(),
                        "reason",
                        nullSafe(request.reason())));
        return AdminTerritoryResponse.from(territory);
    }

    @Transactional
    @CacheEvict(
            value = {"territory-grid", "territory-grid-etag"},
            allEntries = true)
    public AdminTerritoryResponse changeAuctionEnabled(
            Long adminUserId, Long territoryId, AdminToggleAuctionRequest request) {
        Territory territory =
                territoryRepository
                        .findById(territoryId)
                        .orElseThrow(() -> new CustomException(ErrorCode.TERRITORY_NOT_FOUND));

        boolean before = territory.getAuctionEnabled();
        territory.changeAuctionEnabled(request.enabled());

        adminAuditLogger.record(
                adminUserId,
                "TERRITORY_AUCTION_TOGGLE",
                "TERRITORY",
                territoryId,
                Map.of(
                        "before", before,
                        "after", request.enabled(),
                        "reason", nullSafe(request.reason())));
        return AdminTerritoryResponse.from(territory);
    }

    // IDLE 영토의 재경매 대기(nextAuctionAt)를 건너뛰고 즉시 경매를 시작한다.
    @Transactional
    @CacheEvict(
            value = {"territory-grid", "territory-grid-etag"},
            allEntries = true)
    public AdminTerritoryResponse forceStartAuction(Long adminUserId, Long territoryId) {
        Territory territory =
                territoryRepository
                        .findById(territoryId)
                        .orElseThrow(() -> new CustomException(ErrorCode.TERRITORY_NOT_FOUND));
        validateIdle(territory);

        // 경매 '생성'은 auction-service가 territory.auction-ready 이벤트를 받아 담당(모놀리식은 생성하지 않음).
        territory.startBidding();
        territoryAuctionReadyPublisher.publishFor(territory);

        adminAuditLogger.record(
                adminUserId,
                "TERRITORY_AUCTION_FORCE_START",
                "TERRITORY",
                territoryId,
                Map.of("territoryId", territoryId));
        return AdminTerritoryResponse.from(territory);
    }

    // 선택된 여러 영토의 등급을 일괄 변경. 점유 중인 영토는 보호 대상이라 건너뛰고, 실제 변경된 개수만 반환한다.
    @Transactional
    @CacheEvict(
            value = {"territory-grid", "territory-grid-etag"},
            allEntries = true)
    public AdminBulkResultResponse bulkChangeGrade(
            Long adminUserId, AdminBulkGradeRequest request) {
        TerritoryGrade grade =
                territoryGradeRepository
                        .findByGrade(request.grade())
                        .orElseThrow(
                                () -> new CustomException(ErrorCode.TERRITORY_GRADE_NOT_FOUND));
        List<Long> territoryIds = request.territoryIds().stream().distinct().toList();
        int changed = 0;
        for (Long territoryId : territoryIds) {
            Territory territory = findTerritoryOrThrow(territoryId);
            if (territory.getStatus() == Territory.TerritoryStatus.OCCUPIED) {
                continue;
            }
            String before = territory.getGrade().getGrade();
            territory.changeGrade(grade);
            adminAuditLogger.record(
                    adminUserId,
                    "TERRITORY_GRADE_CHANGE_BULK",
                    "TERRITORY",
                    territoryId,
                    Map.of(
                            "before", before,
                            "after", request.grade(),
                            "reason", nullSafe(request.reason())));
            changed++;
        }
        return new AdminBulkResultResponse(changed);
    }

    // 선택된 여러 영토의 경매 활성/비활성을 일괄 변경 (all-or-nothing).
    @Transactional
    @CacheEvict(
            value = {"territory-grid", "territory-grid-etag"},
            allEntries = true)
    public AdminBulkResultResponse bulkChangeAuction(
            Long adminUserId, AdminBulkTerritoryAuctionRequest request) {
        List<Long> territoryIds = request.territoryIds().stream().distinct().toList();
        for (Long territoryId : territoryIds) {
            Territory territory = findTerritoryOrThrow(territoryId);
            boolean before = territory.getAuctionEnabled();
            territory.changeAuctionEnabled(request.enabled());
            adminAuditLogger.record(
                    adminUserId,
                    "TERRITORY_AUCTION_TOGGLE_BULK",
                    "TERRITORY",
                    territoryId,
                    Map.of(
                            "before", before,
                            "after", request.enabled(),
                            "reason", nullSafe(request.reason())));
        }
        return new AdminBulkResultResponse(territoryIds.size());
    }

    // 선택된 여러 영토 중 IDLE인 것만 즉시 경매 시작 (best-effort, 시작 개수 반환).
    @Transactional
    @CacheEvict(
            value = {"territory-grid", "territory-grid-etag"},
            allEntries = true)
    public AdminBulkResultResponse bulkForceStart(
            Long adminUserId, AdminBulkForceStartRequest request) {
        List<Long> territoryIds = request.territoryIds().stream().distinct().toList();
        int started = 0;
        for (Long territoryId : territoryIds) {
            Territory territory = findTerritoryOrThrow(territoryId);
            if (territory.getStatus() != Territory.TerritoryStatus.IDLE) {
                continue;
            }
            territory.startBidding();
            territoryAuctionReadyPublisher.publishFor(territory);
            adminAuditLogger.record(
                    adminUserId,
                    "TERRITORY_AUCTION_FORCE_START_BULK",
                    "TERRITORY",
                    territoryId,
                    Map.of("territoryId", territoryId));
            started++;
        }
        return new AdminBulkResultResponse(started);
    }

    private Territory findTerritoryOrThrow(Long territoryId) {
        return territoryRepository
                .findById(territoryId)
                .orElseThrow(() -> new CustomException(ErrorCode.TERRITORY_NOT_FOUND));
    }

    private void validateIdle(Territory territory) {
        if (territory.getStatus() != Territory.TerritoryStatus.IDLE) {
            throw new CustomException(ErrorCode.TERRITORY_NOT_IDLE);
        }
    }

    // 점유 중인 영토는 현재 점유자의 생산량·가치 보호를 위해 등급을 바꿀 수 없다.
    private void validateNotOccupied(Territory territory) {
        if (territory.getStatus() == Territory.TerritoryStatus.OCCUPIED) {
            throw new CustomException(ErrorCode.TERRITORY_GRADE_LOCKED_OCCUPIED);
        }
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }
}
