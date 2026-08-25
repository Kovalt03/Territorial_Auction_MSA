package com.territorial.auction.domain.building.controller;

import com.territorial.auction.domain.building.dto.GlobalVaultResponse;
import com.territorial.auction.domain.building.dto.VaultTransferRequest;
import com.territorial.auction.domain.building.dto.VaultTransferResponse;
import com.territorial.auction.domain.building.service.GlobalVaultService;
import com.territorial.auction.global.common.ApiResponse;
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
@RequestMapping("/api/v1/global-vault")
@RequiredArgsConstructor
public class GlobalVaultController {

    private final GlobalVaultService globalVaultService;

    @GetMapping
    public ResponseEntity<ApiResponse<GlobalVaultResponse>> getVault(
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(globalVaultService.getVault(userId)));
    }

    @PostMapping("/transfer")
    public ResponseEntity<ApiResponse<VaultTransferResponse>> transfer(
            @AuthenticationPrincipal Long userId,
            @RequestBody @Valid VaultTransferRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(globalVaultService.transfer(userId, request)));
    }
}
