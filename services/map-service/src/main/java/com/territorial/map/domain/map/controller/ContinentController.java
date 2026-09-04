package com.territorial.map.domain.map.controller;

import com.territorial.auction.global.common.ApiResponse;
import com.territorial.map.domain.map.dto.ContinentListResponse;
import com.territorial.map.domain.map.service.ContinentService;
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
