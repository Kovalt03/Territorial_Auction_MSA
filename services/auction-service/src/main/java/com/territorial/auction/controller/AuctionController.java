package com.territorial.auction.controller;

import com.territorial.auction.dto.AuctionBidHistoryResponse;
import com.territorial.auction.dto.AuctionDetailResponse;
import com.territorial.auction.dto.AuctionListResponse;
import com.territorial.auction.dto.MyBidListResponse;
import com.territorial.auction.dto.PlaceBidRequest;
import com.territorial.auction.dto.PlaceBidResponse;
import com.territorial.auction.dto.TerritoryAuctionHistoryResponse;
import com.territorial.auction.entity.AuctionStatus;
import com.territorial.auction.global.common.ApiResponse;
import com.territorial.auction.service.AuctionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auctions")
@RequiredArgsConstructor
public class AuctionController {

    private final AuctionService auctionService;

    @GetMapping
    public ResponseEntity<ApiResponse<AuctionListResponse>> getAuctions(
            @RequestParam(required = false) Long continentId,
            @RequestParam(required = false) AuctionStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
                ApiResponse.ok(auctionService.getAuctions(continentId, status, pageable)));
    }

    @GetMapping("/my-bids")
    public ResponseEntity<ApiResponse<MyBidListResponse>> getMyBids(
            @RequestHeader("X-User-Id") Long userId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(auctionService.getMyBids(userId, pageable)));
    }

    @GetMapping("/territories/{territoryId}")
    public ResponseEntity<ApiResponse<TerritoryAuctionHistoryResponse>> getTerritoryAuctionHistory(
            @PathVariable Long territoryId) {
        return ResponseEntity.ok(
                ApiResponse.ok(auctionService.getTerritoryAuctionHistory(territoryId)));
    }

    @GetMapping("/{auctionId}")
    public ResponseEntity<ApiResponse<AuctionDetailResponse>> getAuctionDetail(
            @PathVariable Long auctionId) {
        return ResponseEntity.ok(ApiResponse.ok(auctionService.getAuctionDetail(auctionId)));
    }

    @GetMapping("/{auctionId}/bids")
    public ResponseEntity<ApiResponse<AuctionBidHistoryResponse>> getAuctionBidHistory(
            @PathVariable Long auctionId) {
        return ResponseEntity.ok(ApiResponse.ok(auctionService.getAuctionBidHistory(auctionId)));
    }

    @PostMapping("/{auctionId}/bids")
    public ResponseEntity<ApiResponse<PlaceBidResponse>> placeBid(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long auctionId,
            @RequestBody @Valid PlaceBidRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(auctionService.placeBid(userId, auctionId, request)));
    }
}
