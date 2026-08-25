package com.territorial.auction.domain.season.service;

import com.territorial.auction.domain.military.event.SiegeVictoryEvent;
import com.territorial.auction.domain.ranking.event.AuctionSettledEvent;
import com.territorial.auction.domain.season.entity.SeasonMission.MissionTrigger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 게임 이벤트를 받아 미션 진행도를 증가시킨다. XP 적립(SeasonXpService)과는 별개 트랜잭션. */
@Slf4j
@Component
@RequiredArgsConstructor
public class MissionEventListener {

    private final MissionService missionService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleAuctionWin(AuctionSettledEvent event) {
        missionService.recordProgress(event.userId(), MissionTrigger.AUCTION_WIN);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleSiegeVictory(SiegeVictoryEvent event) {
        missionService.recordProgress(event.attackerId(), MissionTrigger.SIEGE_WIN);
    }
}
