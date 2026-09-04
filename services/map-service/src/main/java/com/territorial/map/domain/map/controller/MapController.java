package com.territorial.map.domain.map.controller;

import com.territorial.auction.global.common.ApiResponse;
import com.territorial.map.domain.map.dto.ChangeColorRequest;
import com.territorial.map.domain.map.dto.GridMapResponse;
import com.territorial.map.domain.map.dto.TerritoryDetailResponse;
import com.territorial.map.domain.map.service.MapGridEtagService;
import com.territorial.map.domain.map.service.MapService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.ServletWebRequest;

@RestController
@RequestMapping("/api/v1/map")
@RequiredArgsConstructor
public class MapController {

    private final MapService mapService;
    private final MapGridEtagService mapGridEtagService;

    @GetMapping("/grid")
    public ResponseEntity<ApiResponse<GridMapResponse>> getGridMap(
            @RequestParam(value = "continent", required = false) Long continentId,
            ServletWebRequest request) {
        String eTag = mapGridEtagService.current();
        if (request.checkNotModified(eTag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .cacheControl(CacheControl.noCache().cachePublic())
                    .build();
        }
        GridMapResponse response = mapService.getGridMap(continentId);
        return ResponseEntity.ok()
                .eTag(mapGridEtagService.current())
                .cacheControl(CacheControl.noCache().cachePublic())
                .body(ApiResponse.ok(response));
    }

    @GetMapping("/territories/{territoryId}")
    public ResponseEntity<ApiResponse<TerritoryDetailResponse>> getTerritoryDetail(
            @PathVariable Long territoryId) {
        return ResponseEntity.ok(ApiResponse.ok(mapService.getTerritoryDetail(territoryId)));
    }

    @PatchMapping("/territories/{territoryId}/color")
    public ResponseEntity<ApiResponse<Void>> changeColor(
            @PathVariable Long territoryId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody @Valid ChangeColorRequest request) {
        mapService.changeColor(territoryId, userId, request.colorCode());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
