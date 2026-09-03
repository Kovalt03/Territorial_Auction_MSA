package com.territorial.season.domain.season.controller;

import com.territorial.season.domain.season.dto.CombatSeasonBenefitResponse;
import com.territorial.season.domain.season.service.SeasonPassService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/seasons")
@RequiredArgsConstructor
public class SeasonInternalController {

    private final SeasonPassService seasonPassService;

    @GetMapping("/users/{userId}/combat-benefit")
    public ResponseEntity<CombatSeasonBenefitResponse> getCombatBenefit(@PathVariable Long userId) {
        return ResponseEntity.ok(seasonPassService.getCombatBenefit(userId));
    }
}
