package com.territorial.auction.domain.military.controller;

import com.territorial.auction.domain.military.dto.MySiegeHistoryResponse;
import com.territorial.auction.domain.military.dto.SiegeEventListResponse;
import com.territorial.auction.domain.military.service.MilitaryService;
import com.territorial.auction.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/siege")
@RequiredArgsConstructor
public class SiegeController {

    private final MilitaryService militaryService;

    @GetMapping("/events")
    public ResponseEntity<ApiResponse<SiegeEventListResponse>> getSiegeEvents(
            @RequestParam(defaultValue = "PENDING") String status,
            @PageableDefault(size = 20, sort = "siegeStartAt", direction = Sort.Direction.DESC)
                    Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(militaryService.getSiegeEvents(status, pageable)));
    }

    @GetMapping("/my-history")
    public ResponseEntity<ApiResponse<MySiegeHistoryResponse>> getMySiegeHistory(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "ALL") String result,
            @PageableDefault(size = 20, sort = "resolveAt", direction = Sort.Direction.DESC)
                    Pageable pageable) {
        return ResponseEntity.ok(
                ApiResponse.ok(militaryService.getMySiegeHistory(userId, result, pageable)));
    }
}
