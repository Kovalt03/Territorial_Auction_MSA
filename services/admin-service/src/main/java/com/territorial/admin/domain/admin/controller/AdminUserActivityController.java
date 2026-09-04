package com.territorial.admin.domain.admin.controller;

import com.territorial.admin.domain.admin.dto.AdminBulkNotificationRequest;
import com.territorial.admin.domain.admin.dto.AdminBulkResultResponse;
import com.territorial.admin.domain.admin.dto.AdminSendNotificationRequest;
import com.territorial.admin.domain.admin.dto.AdminUserActiveBidListResponse;
import com.territorial.admin.domain.admin.dto.AdminUserBidListResponse;
import com.territorial.admin.domain.admin.dto.AdminUserTerritoryListResponse;
import com.territorial.admin.domain.admin.service.AdminUserActivityService;
import com.territorial.auction.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserActivityController {

    private final AdminUserActivityService adminUserActivityService;

    @GetMapping("/{userId}/bids")
    public ResponseEntity<ApiResponse<AdminUserBidListResponse>> getBids(
            @PathVariable Long userId,
            @PageableDefault(size = 20, sort = "bidAt", direction = Sort.Direction.DESC)
                    Pageable pageable) {
        return ResponseEntity.ok(
                ApiResponse.ok(adminUserActivityService.getBids(userId, pageable)));
    }

    @GetMapping("/{userId}/active-bids")
    public ResponseEntity<ApiResponse<AdminUserActiveBidListResponse>> getActiveBids(
            @PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(adminUserActivityService.getActiveBids(userId)));
    }

    @GetMapping("/{userId}/territories")
    public ResponseEntity<ApiResponse<AdminUserTerritoryListResponse>> getTerritories(
            @PathVariable Long userId, @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
                ApiResponse.ok(adminUserActivityService.getTerritories(userId, pageable)));
    }

    @PostMapping("/{userId}/notifications")
    public ResponseEntity<ApiResponse<Void>> sendNotification(
            @AuthenticationPrincipal Long adminUserId,
            @PathVariable Long userId,
            @RequestBody @Valid AdminSendNotificationRequest request) {
        adminUserActivityService.sendNotification(adminUserId, userId, request);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/bulk/notifications")
    public ResponseEntity<ApiResponse<AdminBulkResultResponse>> bulkSendNotification(
            @AuthenticationPrincipal Long adminUserId,
            @RequestBody @Valid AdminBulkNotificationRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(
                        adminUserActivityService.bulkSendNotification(adminUserId, request)));
    }
}
