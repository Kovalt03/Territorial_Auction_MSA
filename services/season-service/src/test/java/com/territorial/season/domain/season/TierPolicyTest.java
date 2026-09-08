package com.territorial.season.domain.season;

import static org.assertj.core.api.Assertions.assertThat;

import com.territorial.season.domain.season.entity.UserTrophy.League;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TierPolicyTest {

    @DisplayName("점수 → 리그 — 서브티어 경계값")
    @ParameterizedTest(name = "score={0} → {1}")
    @CsvSource({
        "-100, BRONZE", // 음수 방어 → 최하위
        "0, BRONZE",
        "166, BRONZE", // 500 미만은 BRONZE
        "500, SILVER", // SILVER 시작
        "1499, SILVER",
        "1500, GOLD", // GOLD 시작
        "3999, GOLD",
        "4000, DIAMOND", // DIAMOND 시작
        "7999, DIAMOND",
        "8000, CHAMPION" // CHAMPION 시작
    })
    void calculateLeague_boundaries(int score, League expected) {
        assertThat(TierPolicy.calculateLeague(score)).isEqualTo(expected);
    }

    @DisplayName("시즌 리셋 — 한 서브티어 강등하여 하위 서브티어 최소 점수로")
    @ParameterizedTest(name = "score={0} → resetScore={1}")
    @CsvSource({
        "0, 0", // 최하위는 더 내려가지 않음
        "100, 0", // Bronze3(idx0) → 0
        "167, 0", // Bronze2(idx1) → Bronze3 min(0)
        "500, 334", // Silver3(idx3) → Bronze1 min(334)
        "4000, 2834", // Diamond3(idx9) → Gold1 min(2834)
        "8000, 6667" // Champion(idx12) → Diamond1 min(6667)
    })
    void calculateResetScore_demotesOneSubTier(int score, int expected) {
        assertThat(TierPolicy.calculateResetScore(score)).isEqualTo(expected);
    }
}
