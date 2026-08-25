package com.territorial.auction.domain.admin.controller;

import com.territorial.auction.domain.admin.dto.AdminGrantItemRequest;
import com.territorial.auction.domain.admin.dto.AdminItemListResponse;
import com.territorial.auction.domain.admin.dto.AdminItemResponse;
import com.territorial.auction.domain.admin.dto.AdminUpdateItemRequest;
import com.territorial.auction.domain.admin.service.AdminItemService;
import com.territorial.auction.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/items")
@RequiredArgsConstructor
public class AdminItemController {

    private final AdminItemService adminItemService;

    @GetMapping
    public ResponseEntity<ApiResponse<AdminItemListResponse>> getItems() {
        return ResponseEntity.ok(ApiResponse.ok(adminItemService.getItems()));
    }

    @PatchMapping("/{itemId}")
    public ResponseEntity<ApiResponse<AdminItemResponse>> updateItem(
            @AuthenticationPrincipal Long adminUserId,
            @PathVariable Long itemId,
            @RequestBody @Valid AdminUpdateItemRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(adminItemService.updateItem(adminUserId, itemId, request)));
    }

    @PostMapping("/grant")
    public ResponseEntity<ApiResponse<Void>> grantItem(
            @AuthenticationPrincipal Long adminUserId,
            @RequestBody @Valid AdminGrantItemRequest request) {
        adminItemService.grantItem(adminUserId, request);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
