package com.territorial.map.internal.admin;

import com.territorial.map.internal.admin.dto.AdminAffectedResult;
import com.territorial.map.internal.admin.dto.AdminContinentCompositionResponse;
import com.territorial.map.internal.admin.dto.AdminGradeDistributionResult;
import com.territorial.map.internal.admin.dto.AdminMapRequests;
import com.territorial.map.internal.admin.dto.AdminStatusCounts;
import com.territorial.map.internal.admin.dto.AdminTerritoryChangeResult;
import com.territorial.map.internal.admin.dto.AdminTerritoryView;
import com.territorial.map.internal.admin.dto.AdminUserTerritoryPage;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 영토·대륙 관리 계약. 모놀리식 admin 도메인이 감사 로그를 남기며 위임 호출한다. */
@RestController
@RequestMapping("/internal/admin")
@RequiredArgsConstructor
public class MapAdminInternalController {

    private final MapAdminService mapAdminService;

    @GetMapping("/continents/compositions")
    public ResponseEntity<AdminContinentCompositionResponse> getCompositions() {
        return ResponseEntity.ok(mapAdminService.getCompositions());
    }

    @PostMapping("/continents/{continentId}/grade-distribution")
    public ResponseEntity<AdminGradeDistributionResult> applyGradeDistribution(
            @PathVariable Long continentId,
            @RequestBody AdminMapRequests.GradeDistribution request) {
        return ResponseEntity.ok(
                mapAdminService.applyGradeDistribution(continentId, request.distribution()));
    }

    @PostMapping("/continents/{continentId}/auction-toggle")
    public ResponseEntity<AdminAffectedResult> changeContinentAuction(
            @PathVariable Long continentId, @RequestBody AdminMapRequests.Toggle request) {
        return ResponseEntity.ok(
                mapAdminService.changeContinentAuction(continentId, request.enabled()));
    }

    @GetMapping("/continents/{continentId}/territories")
    public ResponseEntity<List<AdminTerritoryView>> getContinentTerritories(
            @PathVariable Long continentId) {
        return ResponseEntity.ok(mapAdminService.getContinentTerritories(continentId));
    }

    @PostMapping("/territories/{territoryId}/grade")
    public ResponseEntity<AdminTerritoryChangeResult> changeGrade(
            @PathVariable Long territoryId, @RequestBody AdminMapRequests.Grade request) {
        return ResponseEntity.ok(mapAdminService.changeGrade(territoryId, request.grade()));
    }

    @PostMapping("/territories/{territoryId}/auction-toggle")
    public ResponseEntity<AdminTerritoryChangeResult> changeAuctionEnabled(
            @PathVariable Long territoryId, @RequestBody AdminMapRequests.Toggle request) {
        return ResponseEntity.ok(
                mapAdminService.changeAuctionEnabled(territoryId, request.enabled()));
    }

    @PostMapping("/territories/{territoryId}/force-start")
    public ResponseEntity<AdminTerritoryChangeResult> forceStartAuction(
            @PathVariable Long territoryId) {
        return ResponseEntity.ok(mapAdminService.forceStartAuction(territoryId));
    }

    @PostMapping("/territories/grade-bulk")
    public ResponseEntity<List<AdminTerritoryChangeResult>> bulkChangeGrade(
            @RequestBody AdminMapRequests.BulkGrade request) {
        return ResponseEntity.ok(
                mapAdminService.bulkChangeGrade(request.grade(), request.territoryIds()));
    }

    @PostMapping("/territories/auction-toggle-bulk")
    public ResponseEntity<List<AdminTerritoryChangeResult>> bulkChangeAuction(
            @RequestBody AdminMapRequests.BulkToggle request) {
        return ResponseEntity.ok(
                mapAdminService.bulkChangeAuction(request.enabled(), request.territoryIds()));
    }

    @PostMapping("/territories/force-start-bulk")
    public ResponseEntity<List<AdminTerritoryChangeResult>> bulkForceStart(
            @RequestBody AdminMapRequests.BulkIds request) {
        return ResponseEntity.ok(mapAdminService.bulkForceStart(request.territoryIds()));
    }

    @GetMapping("/users/{userId}/territories")
    public ResponseEntity<AdminUserTerritoryPage> getUserTerritories(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(mapAdminService.getUserTerritories(userId, page, size));
    }

    @GetMapping("/dashboard/territory-status-counts")
    public ResponseEntity<AdminStatusCounts> getStatusCounts() {
        return ResponseEntity.ok(mapAdminService.getStatusCounts());
    }
}
