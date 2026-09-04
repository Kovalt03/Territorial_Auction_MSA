package com.territorial.auction.domain.map.controller;

import com.territorial.auction.domain.map.dto.ContinentTrophyBandResponse;
import com.territorial.auction.domain.map.service.ContinentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 대륙 계약. ranking-service가 대륙 랭킹 트로피 밴드를 조회한다(대륙은 공유 커널 map 소유). */
@RestController
@RequestMapping("/internal/continents")
@RequiredArgsConstructor
public class ContinentInternalController {

    private final ContinentService continentService;

    @GetMapping("/{id}/trophy-band")
    public ResponseEntity<ContinentTrophyBandResponse> getTrophyBand(@PathVariable Long id) {
        return ResponseEntity.ok(continentService.getTrophyBand(id));
    }
}
