package com.territorial.auction.domain.user.controller;

import com.territorial.auction.domain.user.dto.WishlistResponse;
import com.territorial.auction.domain.user.service.WishlistService;
import com.territorial.auction.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/me/wishlist")
@RequiredArgsConstructor
public class WishlistController {

    private final WishlistService wishlistService;

    @GetMapping
    public ResponseEntity<ApiResponse<WishlistResponse>> getWishlist(
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(wishlistService.getWishlist(userId)));
    }

    @PostMapping("/{territoryId}")
    public ResponseEntity<ApiResponse<Void>> addWishlist(
            @AuthenticationPrincipal Long userId, @PathVariable Long territoryId) {
        wishlistService.addWishlist(userId, territoryId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @DeleteMapping("/{territoryId}")
    public ResponseEntity<ApiResponse<Void>> removeWishlist(
            @AuthenticationPrincipal Long userId, @PathVariable Long territoryId) {
        wishlistService.removeWishlist(userId, territoryId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
