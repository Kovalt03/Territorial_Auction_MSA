package com.territorial.user.domain.user.controller;

import com.territorial.auction.global.common.ApiResponse;
import com.territorial.user.domain.user.dto.WishlistResponse;
import com.territorial.user.domain.user.service.WishlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
