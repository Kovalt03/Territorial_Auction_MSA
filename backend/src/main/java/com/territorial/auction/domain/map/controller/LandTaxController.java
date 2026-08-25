package com.territorial.auction.domain.map.controller;

import com.territorial.auction.domain.map.dto.TaxLogResponse;
import com.territorial.auction.domain.map.dto.TaxStatusResponse;
import com.territorial.auction.domain.map.entity.LandTaxLog.TaxStatus;
import com.territorial.auction.domain.map.service.LandTaxService;
import com.territorial.auction.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/land-tax")
@RequiredArgsConstructor
public class LandTaxController {

    private final LandTaxService landTaxService;

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<TaxStatusResponse>> getLandTaxStatus(
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(landTaxService.getLandTaxStatus(userId)));
    }

    @GetMapping("/logs")
    public ResponseEntity<ApiResponse<TaxLogResponse>> getLandTaxLogs(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) TaxStatus status,
            @PageableDefault(
                            page = 0,
                            size = 10,
                            sort = "chargedAt",
                            direction = Sort.Direction.DESC)
                    Pageable pageable) {
        return ResponseEntity.ok(
                ApiResponse.ok(landTaxService.getLandTaxLogs(userId, status, pageable)));
    }
}
