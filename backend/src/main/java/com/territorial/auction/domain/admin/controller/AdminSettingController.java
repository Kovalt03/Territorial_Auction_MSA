package com.territorial.auction.domain.admin.controller;

import com.territorial.auction.domain.admin.dto.AdminAuctionSettingResponse;
import com.territorial.auction.domain.admin.dto.AdminBalanceSettingResponse;
import com.territorial.auction.domain.admin.dto.AdminToggleAuctionRequest;
import com.territorial.auction.domain.admin.dto.AdminUpdateBalanceRequest;
import com.territorial.auction.domain.admin.service.AdminSettingService;
import com.territorial.auction.global.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/settings")
@RequiredArgsConstructor
public class AdminSettingController {

    private final AdminSettingService adminSettingService;

    @GetMapping("/auction")
    public ResponseEntity<ApiResponse<AdminAuctionSettingResponse>> getAuctionSetting() {
        return ResponseEntity.ok(ApiResponse.ok(adminSettingService.getAuctionSetting()));
    }

    @PatchMapping("/auction")
    public ResponseEntity<ApiResponse<AdminAuctionSettingResponse>> setAuctionEnabled(
            @AuthenticationPrincipal Long userId,
            @RequestBody @Valid AdminToggleAuctionRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(adminSettingService.setAuctionEnabled(userId, request.enabled())));
    }

    @GetMapping("/balance")
    public ResponseEntity<ApiResponse<List<AdminBalanceSettingResponse>>> getBalanceSettings() {
        return ResponseEntity.ok(ApiResponse.ok(adminSettingService.getBalanceSettings()));
    }

    @PatchMapping("/balance")
    public ResponseEntity<ApiResponse<AdminBalanceSettingResponse>> updateBalanceSetting(
            @AuthenticationPrincipal Long userId,
            @RequestBody @Valid AdminUpdateBalanceRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(
                        adminSettingService.updateBalanceSetting(
                                userId, request.key(), request.value())));
    }
}
