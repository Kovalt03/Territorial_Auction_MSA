package com.territorial.season.internal;

import com.territorial.season.internal.dto.SeasonInternalDtos.TrophyView;
import com.territorial.season.internal.dto.SeasonInternalDtos.UserScoreView;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 트로피 데이터 계약. ranking(랭킹 산출·nickname은 조회측이 붙임)·user(프로필)·social(멤버통계) 위임. */
@RestController
@RequestMapping("/internal/trophies")
@RequiredArgsConstructor
public class TrophyInternalController {

    private final SeasonInternalService seasonInternalService;

    @GetMapping("/{userId}")
    public ResponseEntity<TrophyView> getTrophy(@PathVariable Long userId) {
        return seasonInternalService
                .getTrophy(userId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/ranking")
    public ResponseEntity<List<TrophyView>> getRanking(
            @RequestParam int page, @RequestParam int size) {
        return ResponseEntity.ok(seasonInternalService.getTrophyRanking(page, size));
    }

    @GetMapping("/count-above")
    public ResponseEntity<Long> countAbove(@RequestParam int score) {
        return ResponseEntity.ok(seasonInternalService.countTrophyAbove(score));
    }

    @GetMapping("/band")
    public ResponseEntity<List<TrophyView>> getBand(
            @RequestParam int lower,
            @RequestParam int upper,
            @RequestParam int page,
            @RequestParam int size) {
        return ResponseEntity.ok(seasonInternalService.getTrophyBand(lower, upper, page, size));
    }

    @GetMapping("/count-band")
    public ResponseEntity<Long> countBand(@RequestParam int score, @RequestParam int upper) {
        return ResponseEntity.ok(seasonInternalService.countTrophyBand(score, upper));
    }

    @PostMapping("/sum")
    public ResponseEntity<List<UserScoreView>> sumScores(@RequestBody List<Long> userIds) {
        return ResponseEntity.ok(seasonInternalService.sumScores(userIds));
    }
}
