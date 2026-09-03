package com.territorial.season.internal.dto;

import com.territorial.season.domain.season.entity.UserTrophy;
import java.time.LocalDateTime;

/** season-service 내부 계약(ranking·combat·admin·user·map·social 위임) 요청/응답 모음. */
public final class SeasonInternalDtos {

    private SeasonInternalDtos() {}

    public record ActiveSeasonView(
            Long seasonId, Integer seasonNumber, LocalDateTime startedAt, LocalDateTime endedAt) {}

    public record TrophyView(Long userId, int score, String league) {
        public static TrophyView from(UserTrophy t) {
            return new TrophyView(t.getUserId(), t.getScore(), t.getLeague().name());
        }
    }

    public record UserScoreView(Long userId, long totalScore) {}

    // 게임 이벤트 위임(모놀 relay → season): 경매 낙찰·공성 승리로 XP·미션 진행.
    public record GameEventRequest(Long userId, String eventType) {}

    // 시즌패스 혜택(map/LandTax 토지세 면제 보너스 등).
    public record SeasonPassBenefitView(int taxExemptBonus) {}

    // 활성 시즌패스 요약(user 프로필: 추가 건축가·만료 시각). 활성 패스 없으면 204.
    public record UserPassSummaryView(LocalDateTime expiresAt, int extraBuilders) {}
}
