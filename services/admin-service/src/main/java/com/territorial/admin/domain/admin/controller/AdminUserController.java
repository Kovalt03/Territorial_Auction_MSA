package com.territorial.admin.domain.admin.controller;

import com.territorial.admin.client.UserStatus;
import com.territorial.admin.domain.admin.dto.AdminAdjustWalletRequest;
import com.territorial.admin.domain.admin.dto.AdminBulkAdjustWalletRequest;
import com.territorial.admin.domain.admin.dto.AdminBulkChangeStatusRequest;
import com.territorial.admin.domain.admin.dto.AdminBulkResultResponse;
import com.territorial.admin.domain.admin.dto.AdminChangeUserStatusRequest;
import com.territorial.admin.domain.admin.dto.AdminUserDetailResponse;
import com.territorial.admin.domain.admin.dto.AdminUserListResponse;
import com.territorial.admin.domain.admin.service.AdminUserService;
import com.territorial.auction.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public ResponseEntity<ApiResponse<AdminUserListResponse>> getUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UserStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
                    Pageable pageable) {
        return ResponseEntity.ok(
                ApiResponse.ok(adminUserService.getUsers(keyword, status, pageable)));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<AdminUserDetailResponse>> getUser(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(adminUserService.getUser(userId)));
    }

    @PatchMapping("/{userId}/status")
    public ResponseEntity<ApiResponse<AdminUserDetailResponse>> changeStatus(
            @AuthenticationPrincipal Long adminUserId,
            @PathVariable Long userId,
            @RequestBody @Valid AdminChangeUserStatusRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(adminUserService.changeStatus(adminUserId, userId, request)));
    }

    @PostMapping("/{userId}/wallet/adjust")
    public ResponseEntity<ApiResponse<AdminUserDetailResponse>> adjustWallet(
            @AuthenticationPrincipal Long adminUserId,
            @PathVariable Long userId,
            @RequestBody @Valid AdminAdjustWalletRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(adminUserService.adjustWallet(adminUserId, userId, request)));
    }

    @PostMapping("/bulk/wallet-adjust")
    public ResponseEntity<ApiResponse<AdminBulkResultResponse>> bulkAdjustWallet(
            @AuthenticationPrincipal Long adminUserId,
            @RequestBody @Valid AdminBulkAdjustWalletRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(adminUserService.bulkAdjustWallet(adminUserId, request)));
    }

    @PostMapping("/bulk/status")
    public ResponseEntity<ApiResponse<AdminBulkResultResponse>> bulkChangeStatus(
            @AuthenticationPrincipal Long adminUserId,
            @RequestBody @Valid AdminBulkChangeStatusRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(adminUserService.bulkChangeStatus(adminUserId, request)));
    }
}
