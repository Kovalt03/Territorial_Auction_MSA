package com.territorial.ranking.domain.ranking.controller;

import com.territorial.auction.global.common.ApiResponse;
import com.territorial.ranking.domain.ranking.dto.AuctionSpendRankingResponse;
import com.territorial.ranking.domain.ranking.dto.ContinentRankingResponse;
import com.territorial.ranking.domain.ranking.dto.MyRankingResponse;
import com.territorial.ranking.domain.ranking.dto.TerritoryHoldRankingResponse;
import com.territorial.ranking.domain.ranking.dto.TrophyRankingResponse;
import com.territorial.ranking.domain.ranking.service.RankingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 랭킹은 공개 조회 — 로그인 시 게이트웨이가 X-User-Id를 주입하면 내 순위를 함께 채운다(미로그인 시 null). */
@RestController
@RequestMapping("/api/v1/rankings")
@RequiredArgsConstructor
public class RankingController {

    private final RankingService rankingService;

    @GetMapping("/territory-hold")
    public ResponseEntity<ApiResponse<TerritoryHoldRankingResponse>> getTerritoryHoldRanking(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(
                ApiResponse.ok(rankingService.getTerritoryHoldRanking(userId, page, size)));
    }

    @GetMapping("/auction-spend")
    public ResponseEntity<ApiResponse<AuctionSpendRankingResponse>> getAuctionSpendRanking(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(
                ApiResponse.ok(rankingService.getAuctionSpendRanking(userId, page, size)));
    }

    @GetMapping("/trophy")
    public ResponseEntity<ApiResponse<TrophyRankingResponse>> getTrophyRanking(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(
                ApiResponse.ok(rankingService.getTrophyRanking(userId, page, size)));
    }

    @GetMapping("/continent/{continentId}")
    public ResponseEntity<ApiResponse<ContinentRankingResponse>> getContinentRanking(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long continentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(
                ApiResponse.ok(
                        rankingService.getContinentRanking(userId, continentId, page, size)));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MyRankingResponse>> getMyRanking(
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(rankingService.getMyRanking(userId)));
    }
}
