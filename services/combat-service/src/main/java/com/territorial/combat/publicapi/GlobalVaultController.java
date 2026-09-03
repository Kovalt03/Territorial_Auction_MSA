package com.territorial.combat.publicapi;

import com.territorial.auction.global.common.ApiResponse;
import com.territorial.combat.domain.building.dto.GlobalVaultResponse;
import com.territorial.combat.domain.building.dto.VaultTransferRequest;
import com.territorial.combat.domain.building.dto.VaultTransferResponse;
import com.territorial.combat.domain.building.service.GlobalVaultService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/global-vault")
@RequiredArgsConstructor
public class GlobalVaultController {

    private final GlobalVaultService globalVaultService;

    @GetMapping
    public ResponseEntity<ApiResponse<GlobalVaultResponse>> getVault(
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(globalVaultService.getVault(userId)));
    }

    @PostMapping("/transfer")
    public ResponseEntity<ApiResponse<VaultTransferResponse>> transfer(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody @Valid VaultTransferRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(globalVaultService.transfer(userId, request)));
    }
}
