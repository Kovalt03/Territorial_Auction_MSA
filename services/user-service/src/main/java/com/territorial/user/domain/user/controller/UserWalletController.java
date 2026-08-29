package com.territorial.user.domain.user.controller;

import com.territorial.auction.global.common.ApiResponse;
import com.territorial.user.domain.user.dto.ChargeApRequest;
import com.territorial.user.domain.user.dto.ChargeApResponse;
import com.territorial.user.domain.user.dto.MyWalletResponse;
import com.territorial.user.domain.user.service.UserWalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class UserWalletController {

    private final UserWalletService userWalletService;

    @GetMapping("/wallet")
    public ResponseEntity<ApiResponse<MyWalletResponse>> getMyWallet(
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(userWalletService.getMyWallet(userId)));
    }

    @PostMapping("/ap/charge")
    public ResponseEntity<ApiResponse<ChargeApResponse>> chargeAp(
            @AuthenticationPrincipal Long userId, @RequestBody @Valid ChargeApRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(userWalletService.chargeAp(userId, request)));
    }
}
