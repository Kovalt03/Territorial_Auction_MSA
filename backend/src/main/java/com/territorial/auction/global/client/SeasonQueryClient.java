package com.territorial.auction.global.client;

import java.time.LocalDateTime;
import java.util.Optional;

/** season-service 조회 계약. 활성 시즌·시즌패스 혜택을 map·user·ranking·admin이 위임 조회한다. */
public interface SeasonQueryClient {

    Optional<ActiveSeason> getActiveSeason();

    /** 토지세 면제 보너스(만료 필터 반영, 없으면 0). */
    int getTaxExemptBonus(Long userId);

    /** user 프로필용 활성 시즌패스 요약(없으면 empty). */
    Optional<UserPassSummary> getUserPassSummary(Long userId);

    record ActiveSeason(
            Long seasonId, Integer seasonNumber, LocalDateTime startedAt, LocalDateTime endedAt) {}

    record UserPassSummary(LocalDateTime expiresAt, int extraBuilders) {}
}
