package com.territorial.auction.global.client;

import java.time.LocalDateTime;

/** ranking-service 영토 점유 종료 위임. map(모놀리식)이 점유 만료 시 호출한다. */
public interface RankingHoldClient {

    void closeHold(Long userId, Long seasonId, Long territoryId, LocalDateTime heldUntil);
}
