package com.territorial.auction.domain.user.controller;

import com.territorial.auction.domain.user.dto.*;
import com.territorial.auction.domain.user.service.PaymentService;
import com.territorial.auction.domain.user.service.UserService;
import com.territorial.auction.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final PaymentService paymentService;

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getUser(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getUserProfile(userId)));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MyProfileResponse>> getMe(
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getMyProfile(userId)));
    }

    @GetMapping("/me/territories")
    public ResponseEntity<ApiResponse<MyTerritoryResponse>> getMyTerritories(
            @AuthenticationPrincipal Long userId,
            @PageableDefault(page = 0, size = 10, sort = "id", direction = Sort.Direction.DESC)
                    Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getMyTerritories(userId, pageable)));
    }

    @GetMapping("/me/wallet")
    public ResponseEntity<ApiResponse<MyWalletResponse>> getMyWallet(
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getMyWallet(userId)));
    }

    @PostMapping("/me/ap/charge")
    public ResponseEntity<ApiResponse<ChargeApResponse>> chargeAp(
            @AuthenticationPrincipal Long userId, @RequestBody @Valid ChargeApRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(paymentService.chargeAp(userId, request)));
    }
}
