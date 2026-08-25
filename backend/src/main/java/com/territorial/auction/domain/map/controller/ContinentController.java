package com.territorial.auction.domain.map.controller;

import com.territorial.auction.domain.map.dto.ContinentListResponse;
import com.territorial.auction.domain.map.service.ContinentService;
import com.territorial.auction.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/continents")
@RequiredArgsConstructor
public class ContinentController {

    private final ContinentService continentService;

    @GetMapping()
    public ResponseEntity<ApiResponse<ContinentListResponse>> getContinents() {
        return ResponseEntity.ok(ApiResponse.ok(continentService.getContinents()));
    }
}
