package com.territorial.combat.publicapi;

import com.territorial.auction.global.common.ApiResponse;
import com.territorial.combat.domain.military.dto.MySiegeHistoryResponse;
import com.territorial.combat.domain.military.dto.SiegeEventListResponse;
import com.territorial.combat.domain.military.service.MilitaryQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/siege")
@RequiredArgsConstructor
public class SiegeController {

    private final MilitaryQueryService queryService;

    @GetMapping("/events")
    public ResponseEntity<ApiResponse<SiegeEventListResponse>> getSiegeEvents(
            @RequestParam(defaultValue = "PENDING") String status,
            @PageableDefault(size = 20, sort = "siegeStartAt", direction = Sort.Direction.DESC)
                    Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(queryService.getSiegeEvents(status, pageable)));
    }

    @GetMapping("/my-history")
    public ResponseEntity<ApiResponse<MySiegeHistoryResponse>> getMySiegeHistory(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "ALL") String result,
            @PageableDefault(size = 20, sort = "resolveAt", direction = Sort.Direction.DESC)
                    Pageable pageable) {
        return ResponseEntity.ok(
                ApiResponse.ok(queryService.getMySiegeHistory(userId, result, pageable)));
    }
}
