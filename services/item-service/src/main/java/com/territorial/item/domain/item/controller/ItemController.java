package com.territorial.item.domain.item.controller;

import com.territorial.auction.global.common.ApiResponse;
import com.territorial.item.domain.item.dto.ItemInventoryResponse;
import com.territorial.item.domain.item.dto.ItemListResponse;
import com.territorial.item.domain.item.dto.PurchaseItemRequest;
import com.territorial.item.domain.item.dto.PurchaseItemResponse;
import com.territorial.item.domain.item.dto.UseItemRequest;
import com.territorial.item.domain.item.dto.UseItemResponse;
import com.territorial.item.domain.item.service.ItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/items")
@RequiredArgsConstructor
public class ItemController {

    private final ItemService itemService;

    @GetMapping
    public ResponseEntity<ApiResponse<ItemListResponse>> getItems(
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(itemService.getItems(userId)));
    }

    @PostMapping("/purchase")
    public ResponseEntity<ApiResponse<PurchaseItemResponse>> purchaseItem(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody @Valid PurchaseItemRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(itemService.purchaseItem(userId, request)));
    }

    @PostMapping("/use")
    public ResponseEntity<ApiResponse<UseItemResponse>> useItem(
            @RequestHeader("X-User-Id") Long userId, @RequestBody @Valid UseItemRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(itemService.useItem(userId, request)));
    }

    @GetMapping("/inventory")
    public ResponseEntity<ApiResponse<ItemInventoryResponse>> getInventory(
            @RequestHeader("X-User-Id") Long userId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(itemService.getInventory(userId, pageable)));
    }
}
