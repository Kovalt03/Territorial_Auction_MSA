package com.territorial.season.domain.season.controller;

import com.territorial.season.domain.season.dto.CombatSeasonBenefitResponse;
import com.territorial.season.domain.season.service.SeasonPassService;
import com.territorial.season.internal.SeasonInternalService;
import com.territorial.season.internal.dto.SeasonInternalDtos.ActiveSeasonView;
import com.territorial.season.internal.dto.SeasonInternalDtos.GameEventRequest;
import com.territorial.season.internal.dto.SeasonInternalDtos.SeasonPassBenefitView;
import com.territorial.season.internal.dto.SeasonInternalDtos.UserPassSummaryView;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/seasons")
@RequiredArgsConstructor
public class SeasonInternalController {

    private final SeasonPassService seasonPassService;
    private final SeasonInternalService seasonInternalService;

    // combat-service: 시즌 패스 건설 혜택(건설시간 감소·추가 건축가)
    @GetMapping("/users/{userId}/combat-benefit")
    public ResponseEntity<CombatSeasonBenefitResponse> getCombatBenefit(@PathVariable Long userId) {
        return ResponseEntity.ok(seasonPassService.getCombatBenefit(userId));
    }

    // ranking·combat·admin·map: 활성 시즌 조회(없으면 204)
    @GetMapping("/active")
    public ResponseEntity<ActiveSeasonView> getActiveSeason() {
        return seasonInternalService
                .getActiveSeason()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    // map/LandTax: 시즌 패스 토지세 면제 보너스
    @GetMapping("/users/{userId}/pass-benefit")
    public ResponseEntity<SeasonPassBenefitView> getSeasonPassBenefit(@PathVariable Long userId) {
        return ResponseEntity.ok(seasonInternalService.getSeasonPassBenefit(userId));
    }

    // user 프로필: 활성 시즌패스 요약(추가 건축가·만료 시각). 없으면 204.
    @GetMapping("/users/{userId}/pass-summary")
    public ResponseEntity<UserPassSummaryView> getUserPassSummary(@PathVariable Long userId) {
        return seasonInternalService
                .getUserPassSummary(userId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    // 모놀 relay: 경매 낙찰·공성 승리 → XP·미션 위임
    @PostMapping("/game-events")
    public ResponseEntity<Void> handleGameEvent(@RequestBody GameEventRequest request) {
        seasonInternalService.handleGameEvent(request.userId(), request.eventType());
        return ResponseEntity.ok().build();
    }
}
