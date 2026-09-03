package com.territorial.season.domain.season.controller;

import com.territorial.auction.global.common.ApiResponse;
import com.territorial.season.domain.season.dto.ClaimMissionResponse;
import com.territorial.season.domain.season.dto.ClaimRewardResponse;
import com.territorial.season.domain.season.dto.MissionListResponse;
import com.territorial.season.domain.season.dto.MySeasonPassResponse;
import com.territorial.season.domain.season.dto.PurchaseLevelResponse;
import com.territorial.season.domain.season.dto.PurchaseSeasonPassResponse;
import com.territorial.season.domain.season.dto.SeasonPassResponse;
import com.territorial.season.domain.season.service.MissionService;
import com.territorial.season.domain.season.service.SeasonPassService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/season-pass")
@RequiredArgsConstructor
public class SeasonPassController {

    private final SeasonPassService seasonPassService;
    private final MissionService missionService;

    @GetMapping()
    public ResponseEntity<ApiResponse<SeasonPassResponse>> getSeasonPass(
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(seasonPassService.getProgress(userId)));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MySeasonPassResponse>> getMySeasonPass(
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(seasonPassService.getMyPass(userId)));
    }

    @PostMapping("/purchase")
    public ResponseEntity<ApiResponse<PurchaseSeasonPassResponse>> purchaseSeasonPass(
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(seasonPassService.purchase(userId)));
    }

    @PostMapping("/level-up")
    public ResponseEntity<ApiResponse<PurchaseLevelResponse>> purchaseLevel(
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(seasonPassService.purchaseLevel(userId)));
    }

    @GetMapping("/missions")
    public ResponseEntity<ApiResponse<MissionListResponse>> getMissions(
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(missionService.getMissions(userId)));
    }

    @PostMapping("/missions/{missionId}/claim")
    public ResponseEntity<ApiResponse<ClaimMissionResponse>> claimMission(
            @RequestHeader("X-User-Id") Long userId, @PathVariable Long missionId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(missionService.claimMission(userId, missionId)));
    }

    @PostMapping("/rewards/{rewardId}/claim")
    public ResponseEntity<ApiResponse<ClaimRewardResponse>> claimReward(
            @RequestHeader("X-User-Id") Long userId, @PathVariable Long rewardId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(seasonPassService.claimReward(userId, rewardId)));
    }
}
