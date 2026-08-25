package com.territorial.auction.domain.item.controller;

import com.territorial.auction.domain.item.dto.ItemInventoryResponse;
import com.territorial.auction.domain.item.dto.ItemListResponse;
import com.territorial.auction.domain.item.dto.PurchaseItemRequest;
import com.territorial.auction.domain.item.dto.PurchaseItemResponse;
import com.territorial.auction.domain.item.dto.UseItemRequest;
import com.territorial.auction.domain.item.dto.UseItemResponse;
import com.territorial.auction.domain.item.service.ItemService;
import com.territorial.auction.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/items")
@RequiredArgsConstructor
public class ItemController {

    private final ItemService itemService;

    @GetMapping
    public ResponseEntity<ApiResponse<ItemListResponse>> getItems(
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(itemService.getItems(userId)));
    }

    @PostMapping("/purchase")
    public ResponseEntity<ApiResponse<PurchaseItemResponse>> purchaseItem(
            @AuthenticationPrincipal Long userId, @RequestBody @Valid PurchaseItemRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(itemService.purchaseItem(userId, request)));
    }

    @PostMapping("/use")
    public ResponseEntity<ApiResponse<UseItemResponse>> useItem(
            @AuthenticationPrincipal Long userId, @RequestBody @Valid UseItemRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(itemService.useItem(userId, request)));
    }

    @GetMapping("/inventory")
    public ResponseEntity<ApiResponse<ItemInventoryResponse>> getInventory(
            @AuthenticationPrincipal Long userId, @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(itemService.getInventory(userId, pageable)));
    }
}
