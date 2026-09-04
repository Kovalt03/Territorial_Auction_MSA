package com.territorial.admin.domain.admin.controller;

import com.territorial.admin.domain.admin.dto.AdminSeasonPassResponse;
import com.territorial.admin.domain.admin.dto.AdminUpdateSeasonPassRequest;
import com.territorial.admin.domain.admin.service.AdminSeasonPassService;
import com.territorial.auction.global.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/season-passes")
@RequiredArgsConstructor
public class AdminSeasonPassController {

    private final AdminSeasonPassService adminSeasonPassService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminSeasonPassResponse>>> getSeasonPasses() {
        return ResponseEntity.ok(ApiResponse.ok(adminSeasonPassService.getSeasonPasses()));
    }

    @PatchMapping("/{seasonPassId}")
    public ResponseEntity<ApiResponse<AdminSeasonPassResponse>> updateSeasonPass(
            @AuthenticationPrincipal Long adminUserId,
            @PathVariable Long seasonPassId,
            @RequestBody @Valid AdminUpdateSeasonPassRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(adminSeasonPassService.update(adminUserId, seasonPassId, request)));
    }
}
