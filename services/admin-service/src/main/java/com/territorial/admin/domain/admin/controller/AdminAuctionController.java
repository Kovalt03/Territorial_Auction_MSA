package com.territorial.admin.domain.admin.controller;

import com.territorial.admin.domain.admin.dto.AdminAuctionListResponse;
import com.territorial.admin.domain.admin.service.AdminAuctionService;
import com.territorial.auction.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/auctions")
@RequiredArgsConstructor
public class AdminAuctionController {

    private final AdminAuctionService adminAuctionService;

    @GetMapping
    public ResponseEntity<ApiResponse<AdminAuctionListResponse>> getActiveAuctions(
            @PageableDefault(size = 20, sort = "endAt", direction = Sort.Direction.ASC)
                    Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(adminAuctionService.getActiveAuctions(pageable)));
    }

    @PostMapping("/{auctionId}/settle")
    public ResponseEntity<ApiResponse<Void>> forceSettle(
            @AuthenticationPrincipal Long adminUserId, @PathVariable Long auctionId) {
        adminAuctionService.forceSettle(adminUserId, auctionId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/{auctionId}/cancel")
    public ResponseEntity<ApiResponse<Void>> forceCancel(
            @AuthenticationPrincipal Long adminUserId, @PathVariable Long auctionId) {
        adminAuctionService.forceCancel(adminUserId, auctionId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
