package com.territorial.map.domain.map;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LandTaxPolicyTest {

    @DisplayName("과세 대상 수에 따른 일일 토지세 — 구간 경계값")
    @ParameterizedTest(name = "taxableCount={0} → {1} GP")
    @CsvSource({
        "-5, 0", // 음수(면제 초과) → 0
        "0, 0", // 과세 대상 없음 → 0
        "1, 50", // 1~3 구간 시작
        "3, 50", // 1~3 구간 끝
        "4, 150", // 4~7 구간 시작
        "7, 150", // 4~7 구간 끝
        "8, 400", // 8+ 구간 시작
        "100, 400" // 8+ 구간 유지
    })
    void calculateDailyTax_bracketBoundaries(int taxableCount, int expected) {
        assertThat(LandTaxPolicy.calculateDailyTax(taxableCount)).isEqualTo(expected);
    }

    @DisplayName("정책 상수 — 기본 면제 3개, 시즌패스 추가 면제 2개")
    @org.junit.jupiter.api.Test
    void policyConstants() {
        assertThat(LandTaxPolicy.BASE_EXEMPT_COUNT).isEqualTo(3);
        assertThat(LandTaxPolicy.SEASON_PASS_EXEMPT_BONUS).isEqualTo(2);
    }
}
