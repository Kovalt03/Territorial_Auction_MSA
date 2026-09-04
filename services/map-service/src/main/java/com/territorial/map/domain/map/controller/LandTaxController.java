package com.territorial.map.domain.map.controller;

import com.territorial.auction.global.common.ApiResponse;
import com.territorial.map.domain.map.dto.TaxLogResponse;
import com.territorial.map.domain.map.dto.TaxStatusResponse;
import com.territorial.map.domain.map.entity.LandTaxLog.TaxStatus;
import com.territorial.map.domain.map.service.LandTaxService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
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
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(landTaxService.getLandTaxStatus(userId)));
    }

    @GetMapping("/logs")
    public ResponseEntity<ApiResponse<TaxLogResponse>> getLandTaxLogs(
            @RequestHeader("X-User-Id") Long userId,
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
