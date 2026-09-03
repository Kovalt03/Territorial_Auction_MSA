package com.territorial.auction.domain.admin.client;

import java.time.LocalDateTime;
import java.util.List;

/** season-service admin 위임 계약. 시즌 관리(목록·생성·종료)와 시즌패스 편집(목록·수정). 감사 로그는 모놀 admin이 남긴다. */
public interface SeasonAdminClient {

    List<SeasonView> getSeasons();

    SeasonView createSeason(LocalDateTime startedAt, LocalDateTime endedAt);

    SeasonView endSeason(Long seasonId);

    List<SeasonPassView> getSeasonPasses();

    SeasonPassView updateSeasonPass(Long seasonPassId, UpdateSeasonPassCommand command);

    record SeasonView(
            Long seasonId,
            Integer seasonNumber,
            LocalDateTime startedAt,
            LocalDateTime endedAt,
            LocalDateTime processedAt) {}

    record SeasonPassView(
            Long seasonPassId,
            String name,
            int costAp,
            int durationDays,
            int islandBonusPct,
            int extraBuilders,
            int taxExemptBonus,
            int buildTimeReductionPct) {}

    record UpdateSeasonPassCommand(
            Integer costAp,
            Integer durationDays,
            Integer islandBonusPct,
            Integer extraBuilders,
            Integer taxExemptBonus,
            Integer buildTimeReductionPct) {}
}
