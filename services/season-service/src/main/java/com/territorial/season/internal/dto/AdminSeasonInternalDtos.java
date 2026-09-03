package com.territorial.season.internal.dto;

import com.territorial.season.domain.season.entity.Season;
import com.territorial.season.domain.season.entity.SeasonPass;
import java.time.LocalDateTime;

/** admin(모놀리식 관리자) 시즌·시즌패스 관리 위임 계약 요청/응답. 감사 로그는 모놀리식이 담당한다. */
public final class AdminSeasonInternalDtos {

    private AdminSeasonInternalDtos() {}

    // 상태(status)는 조회 시점 기준이라 모놀리식이 계산한다 — 여기서는 원시 필드만 노출.
    public record AdminSeasonView(
            Long seasonId,
            Integer seasonNumber,
            LocalDateTime startedAt,
            LocalDateTime endedAt,
            LocalDateTime processedAt) {
        public static AdminSeasonView from(Season s) {
            return new AdminSeasonView(
                    s.getId(), s.getSeasonNumber(), s.getStartedAt(), s.getEndedAt(),
                    s.getProcessedAt());
        }
    }

    public record AdminCreateSeasonRequest(LocalDateTime startedAt, LocalDateTime endedAt) {}

    public record AdminSeasonPassView(
            Long seasonPassId,
            String name,
            int costAp,
            int durationDays,
            int islandBonusPct,
            int extraBuilders,
            int taxExemptBonus,
            int buildTimeReductionPct) {
        public static AdminSeasonPassView from(SeasonPass pass) {
            return new AdminSeasonPassView(
                    pass.getId(),
                    pass.getName(),
                    pass.getCostAp(),
                    pass.getDurationDays(),
                    pass.getIslandBonusPct(),
                    pass.getExtraBuilders(),
                    pass.getTaxExemptBonus(),
                    pass.getBuildTimeReductionPct());
        }
    }

    public record AdminUpdateSeasonPassRequest(
            Integer costAp,
            Integer durationDays,
            Integer islandBonusPct,
            Integer extraBuilders,
            Integer taxExemptBonus,
            Integer buildTimeReductionPct) {}
}
