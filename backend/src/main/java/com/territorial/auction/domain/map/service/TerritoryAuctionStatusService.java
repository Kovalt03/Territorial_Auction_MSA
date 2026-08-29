package com.territorial.auction.domain.map.service;

import com.territorial.auction.domain.map.entity.TerritoryAuctionStatus;
import com.territorial.auction.domain.map.repository.TerritoryAuctionStatusRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * map 읽기 프로젝션(territory_auction_status) 갱신. auction-service의 이벤트를 소비해 '경매중' 상태를 유지한다. 프로젝션은
 * eventual-consistent read-model이며, MapService 조회 시 endAt으로 한 번 더 걸러 누락된 close 이벤트를 자가 치유한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TerritoryAuctionStatusService {

    private final TerritoryAuctionStatusRepository repository;

    /** 경매 생성 — territoryId PK로 upsert (재경매 시 최신 경매로 덮어씀). */
    @Transactional
    public void open(Long territoryId, Long auctionId, int currentPrice, LocalDateTime endAt) {
        repository.save(
                TerritoryAuctionStatus.builder()
                        .territoryId(territoryId)
                        .auctionId(auctionId)
                        .currentPrice(currentPrice)
                        .endAt(endAt)
                        .build());
    }

    /** 입찰 — 현재가·종료시각 갱신. 아직 open 이벤트가 안 온 경우(순서 역전) 스킵 후 다음 이벤트로 자가 치유. */
    @Transactional
    public void updateBid(Long auctionId, int currentPrice, LocalDateTime endAt) {
        repository
                .findByAuctionId(auctionId)
                .ifPresent(status -> status.updateBid(currentPrice, endAt));
    }

    /** 경매 종료(낙찰·무낙찰 공통) — 해당 경매의 행 제거. 재경매로 덮인 최신 행은 auctionId 불일치로 보존. */
    @Transactional
    public void close(Long auctionId) {
        repository.deleteByAuctionId(auctionId);
    }
}
