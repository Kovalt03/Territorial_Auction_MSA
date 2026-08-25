package com.territorial.auction.domain.admin.controller;

import com.territorial.auction.domain.admin.dto.AdminUnitLevelSpecsRequest;
import com.territorial.auction.domain.admin.dto.AdminUnitLevelSpecsRequest.UnitLevelValues;
import com.territorial.auction.domain.admin.dto.AdminUnitTypeResponse;
import com.territorial.auction.domain.admin.dto.AdminUpdateUnitTypeRequest;
import com.territorial.auction.domain.admin.service.AdminUnitService;
import com.territorial.auction.global.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/unit-types")
@RequiredArgsConstructor
public class AdminUnitController {

    private final AdminUnitService adminUnitService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminUnitTypeResponse>>> getUnitTypes() {
        return ResponseEntity.ok(ApiResponse.ok(adminUnitService.getUnitTypes()));
    }

    @PatchMapping("/{unitTypeId}")
    public ResponseEntity<ApiResponse<AdminUnitTypeResponse>> updateUnitType(
            @AuthenticationPrincipal Long adminUserId,
            @PathVariable Long unitTypeId,
            @RequestBody @Valid AdminUpdateUnitTypeRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(adminUnitService.update(adminUserId, unitTypeId, request)));
    }

    @GetMapping("/{unitTypeId}/level-specs")
    public ResponseEntity<ApiResponse<Map<Integer, UnitLevelValues>>> getLevelSpecs(
            @PathVariable Long unitTypeId) {
        return ResponseEntity.ok(ApiResponse.ok(adminUnitService.getLevelSpecs(unitTypeId)));
    }

    @PatchMapping("/{unitTypeId}/level-specs")
    public ResponseEntity<ApiResponse<Map<Integer, UnitLevelValues>>> updateLevelSpecs(
            @AuthenticationPrincipal Long adminUserId,
            @PathVariable Long unitTypeId,
            @RequestBody AdminUnitLevelSpecsRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(
                        adminUnitService.updateLevelSpecs(
                                adminUserId, unitTypeId, request.specs())));
    }
}
